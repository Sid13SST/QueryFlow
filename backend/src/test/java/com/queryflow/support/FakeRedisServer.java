package com.queryflow.support;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * A tiny in-process Redis stand-in speaking RESP2 over real TCP, so the production
 * Lettuce client, connection factory and timeouts are exercised unchanged.
 *
 * Only what QueryFlow uses is implemented: PING, GET, SET [EX|PX], DEL, EXISTS,
 * KEYS, FLUSHDB, SELECT, CLIENT, QUIT. HELLO is rejected so Lettuce falls back to RESP2.
 *
 * {@link #stop()} closes the listener and every client socket (what a crashed or
 * partitioned node looks like to the client). {@link #start()} listens again on the
 * same port. Data survives stop/start by default, which models a network
 * partition; call {@link #clear()} to model a restarted, empty node.
 *
 * This exists because Docker/Testcontainers is not available in every environment
 * the test suite runs in. docker-compose based drills against real Redis live in
 * scripts/.
 */
public class FakeRedisServer {

    private record Entry(byte[] value, long expiresAtMs) {
        boolean expired() {
            return expiresAtMs > 0 && System.currentTimeMillis() >= expiresAtMs;
        }
    }

    private final int port;
    private final Map<String, Entry> data = new ConcurrentHashMap<>();
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private volatile ServerSocket server;

    public FakeRedisServer(int port) {
        this.port = port;
    }

    private static final java.util.concurrent.atomic.AtomicInteger NEXT_PORT =
            new java.util.concurrent.atomic.AtomicInteger(20_000 + new java.util.Random().nextInt(5_000));

    /**
     * Picks a free local port BELOW the usual OS ephemeral range (32768+ on Linux,
     * 49152+ on Windows/macOS). A port from the ephemeral range can be handed to an
     * outgoing client socket while the fake node is stopped (including Lettuce's own
     * reconnect attempts, which can even self-connect), and the node then cannot
     * re-bind it on restart.
     */
    public static int freePort() {
        for (int i = 0; i < 2_000; i++) {
            int candidate = NEXT_PORT.getAndIncrement();
            if (candidate >= 32_000) {
                NEXT_PORT.set(20_000);
                continue;
            }
            try (ServerSocket s = new ServerSocket()) {
                s.setReuseAddress(true);
                s.bind(new InetSocketAddress("127.0.0.1", candidate));
                return candidate;
            } catch (IOException busy) {
                // try the next one
            }
        }
        throw new IllegalStateException("No free port found in 20000-31999");
    }

    public int getPort() {
        return port;
    }

    public synchronized void start() {
        if (server != null) {
            return;
        }
        // Retry briefly: a just-closed connection to this port can hold it for a moment.
        IOException last = null;
        for (int attempt = 0; attempt < 50 && server == null; attempt++) {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress("127.0.0.1", port));
                server = ss;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (server == null) {
            throw new IllegalStateException("Cannot bind fake redis on " + port, last);
        }
        Thread acceptor = new Thread(this::acceptLoop, "fake-redis-" + port);
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public synchronized void stop() {
        ServerSocket ss = server;
        server = null;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
        for (Socket c : clients) {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }
        clients.clear();
    }

    public boolean isRunning() {
        return server != null;
    }

    public void clear() {
        data.clear();
    }

    public boolean containsKey(String key) {
        Entry e = data.get(key);
        return e != null && !e.expired();
    }

    public int size() {
        return (int) data.values().stream().filter(e -> !e.expired()).count();
    }

    private void acceptLoop() {
        ServerSocket ss = server;
        while (ss != null && !ss.isClosed()) {
            try {
                Socket s = ss.accept();
                s.setTcpNoDelay(true);
                clients.add(s);
                Thread t = new Thread(() -> serve(s), "fake-redis-conn-" + port);
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void serve(Socket socket) {
        try (socket) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            while (true) {
                List<byte[]> cmd = readCommand(in);
                if (cmd == null) {
                    return;
                }
                byte[] reply = handle(cmd);
                out.write(reply);
                out.flush();
            }
        } catch (IOException ignored) {
            // client went away or we were stopped
        } finally {
            clients.remove(socket);
        }
    }

    private byte[] handle(List<byte[]> cmd) {
        String name = str(cmd.get(0)).toUpperCase();
        switch (name) {
            case "PING":
                return simple("PONG");
            case "HELLO":
                return error("ERR unknown command 'HELLO'");
            case "SELECT":
            case "CLIENT":
            case "QUIT":
                return simple("OK");
            case "GET": {
                Entry e = data.get(str(cmd.get(1)));
                if (e == null || e.expired()) {
                    return "$-1\r\n".getBytes(StandardCharsets.UTF_8);
                }
                return bulk(e.value());
            }
            case "SET": {
                long expiresAt = 0;
                for (int i = 3; i + 1 < cmd.size(); i++) {
                    String opt = str(cmd.get(i)).toUpperCase();
                    if (opt.equals("EX")) {
                        expiresAt = System.currentTimeMillis() + Long.parseLong(str(cmd.get(i + 1))) * 1000;
                    } else if (opt.equals("PX")) {
                        expiresAt = System.currentTimeMillis() + Long.parseLong(str(cmd.get(i + 1)));
                    }
                }
                data.put(str(cmd.get(1)), new Entry(cmd.get(2), expiresAt));
                return simple("OK");
            }
            case "SETEX": {
                long expiresAt = System.currentTimeMillis() + Long.parseLong(str(cmd.get(2))) * 1000;
                data.put(str(cmd.get(1)), new Entry(cmd.get(3), expiresAt));
                return simple("OK");
            }
            case "DEL": {
                long n = 0;
                for (int i = 1; i < cmd.size(); i++) {
                    if (data.remove(str(cmd.get(i))) != null) {
                        n++;
                    }
                }
                return integer(n);
            }
            case "EXISTS": {
                long n = 0;
                for (int i = 1; i < cmd.size(); i++) {
                    if (containsKey(str(cmd.get(i)))) {
                        n++;
                    }
                }
                return integer(n);
            }
            case "KEYS": {
                Pattern p = globToRegex(str(cmd.get(1)));
                List<String> keys = new ArrayList<>();
                data.forEach((k, v) -> {
                    if (!v.expired() && p.matcher(k).matches()) {
                        keys.add(k);
                    }
                });
                StringBuilder sb = new StringBuilder("*").append(keys.size()).append("\r\n");
                for (String k : keys) {
                    byte[] b = k.getBytes(StandardCharsets.UTF_8);
                    sb.append('$').append(b.length).append("\r\n").append(k).append("\r\n");
                }
                return sb.toString().getBytes(StandardCharsets.UTF_8);
            }
            case "FLUSHDB":
            case "FLUSHALL":
                data.clear();
                return simple("OK");
            default:
                return error("ERR unknown command '" + name + "'");
        }
    }

    // ------------------------------------------------------------------ RESP

    private static List<byte[]> readCommand(InputStream in) throws IOException {
        int first = in.read();
        if (first == -1) {
            return null;
        }
        if (first != '*') {
            throw new IOException("Inline commands are not supported");
        }
        int count = Integer.parseInt(readLine(in));
        List<byte[]> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (in.read() != '$') {
                throw new IOException("Expected bulk string");
            }
            int len = Integer.parseInt(readLine(in));
            byte[] buf = in.readNBytes(len);
            in.read(); // \r
            in.read(); // \n
            parts.add(buf);
        }
        return parts;
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                in.read();
                break;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }

    private static String str(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    private static byte[] simple(String s) {
        return ("+" + s + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] error(String s) {
        return ("-" + s + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] integer(long n) {
        return (":" + n + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] bulk(byte[] value) {
        byte[] head = ("$" + value.length + "\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[head.length + value.length + 2];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(value, 0, out, head.length, value.length);
        out[out.length - 2] = '\r';
        out[out.length - 1] = '\n';
        return out;
    }

    private static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (char ch : glob.toCharArray()) {
            switch (ch) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> sb.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return Pattern.compile(sb.toString());
    }
}
