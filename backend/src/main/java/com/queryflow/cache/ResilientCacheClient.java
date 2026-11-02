package com.queryflow.cache;

import com.queryflow.dto.CacheNode;
import com.queryflow.service.ConsistentHashService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The only path from application code to Redis.
 *
 * <ul>
 *   <li>Keys are routed with the existing consistent-hash ring. A key whose node
 *       is unhealthy is <b>not</b> re-routed to another node: the caller is told the
 *       cache is UNAVAILABLE and reads PostgreSQL instead. Losing one node therefore
 *       only loses that node's ~1/N of keys, and no node ever holds entries it does
 *       not own (which would escape targeted invalidation after recovery).</li>
 *   <li>Each node has its own {@link CircuitBreaker}; an open circuit costs no
 *       network time.</li>
 *   <li>DELs that cannot reach a node are queued and replayed before that node is
 *       used again, so a node coming back cannot serve entries that were invalidated
 *       while it was unreachable.</li>
 *   <li>Every method swallows Redis errors. Nothing here ever throws to the caller.</li>
 * </ul>
 */
@Service
@Slf4j
public class ResilientCacheClient {

    public enum Outcome { HIT, MISS, UNAVAILABLE }

    public record CacheRead(Outcome outcome, Object value) {
        public boolean isHit() {
            return outcome == Outcome.HIT;
        }
    }

    private record Exec<T>(boolean ok, T value) {
        static <T> Exec<T> failed() {
            return new Exec<>(false, null);
        }
    }

    private final class NodeState {
        final CircuitBreaker breaker;
        final Set<String> pendingDeletes = ConcurrentHashMap.newKeySet();
        volatile boolean flushOnRecovery;

        NodeState() {
            this.breaker = new CircuitBreaker(properties.getFailureThreshold(),
                    properties.getOpenDurationMs(), clockMs);
        }
    }

    private final ConsistentHashService ring;
    private final CacheResilienceProperties properties;
    private final LongSupplier clockMs;
    private final ConcurrentHashMap<String, NodeState> nodeStates = new ConcurrentHashMap<>();
    private final AtomicLong degradedReads = new AtomicLong();

    @Autowired
    public ResilientCacheClient(ConsistentHashService ring, CacheResilienceProperties properties) {
        this(ring, properties, System::currentTimeMillis);
    }

    public ResilientCacheClient(ConsistentHashService ring, CacheResilienceProperties properties,
                                LongSupplier clockMs) {
        this.ring = ring;
        this.properties = properties;
        this.clockMs = clockMs;
    }

    /** Convenience for tests and simple wiring: default breaker settings, real clock. */
    public ResilientCacheClient(ConsistentHashService ring) {
        this(ring, new CacheResilienceProperties());
    }

    // ---------------------------------------------------------------- public API

    public CacheRead get(String key) {
        CacheNode node = ring.route(key);
        if (node == null) {
            degradedReads.incrementAndGet();
            return new CacheRead(Outcome.UNAVAILABLE, null);
        }
        Exec<Object> r = execute(node, t -> {
            try {
                return t.opsForValue().get(key);
            } catch (SerializationException e) {
                // A value we cannot decode is a bad entry, not a sick node: drop it and
                // treat it as a miss so it never counts towards opening the circuit.
                log.warn("CACHE DECODE FAILURE key={} node={} - evicting: {}", key, node.getNodeId(), e.getMessage());
                t.delete(key);
                return null;
            }
        });
        if (!r.ok()) {
            degradedReads.incrementAndGet();
            return new CacheRead(Outcome.UNAVAILABLE, null);
        }
        return r.value() == null ? new CacheRead(Outcome.MISS, null) : new CacheRead(Outcome.HIT, r.value());
    }

    public boolean set(String key, Object value, long ttl, TimeUnit unit) {
        CacheNode node = ring.route(key);
        if (node == null) {
            return false;
        }
        return execute(node, t -> {
            t.opsForValue().set(key, value, ttl, unit);
            return Boolean.TRUE;
        }).ok();
    }

    /**
     * Deletes a key from its owning node. If the node cannot be reached the key is
     * remembered and deleted before the node is used again.
     *
     * @return true if the DEL reached Redis now
     */
    public boolean delete(String key) {
        CacheNode node = ring.route(key);
        if (node == null) {
            return false;
        }
        boolean ok = execute(node, t -> t.delete(key)).ok();
        if (!ok) {
            remember(node, key);
        }
        return ok;
    }

    public boolean hasKey(String key) {
        CacheNode node = ring.route(key);
        if (node == null) {
            return false;
        }
        Exec<Boolean> r = execute(node, t -> t.hasKey(key));
        return r.ok() && Boolean.TRUE.equals(r.value());
    }

    /** Number of keys matching a pattern on one node, or -1 when the node is unavailable. */
    public long countKeys(CacheNode node, String pattern) {
        Exec<Set<String>> r = execute(node, t -> t.keys(pattern));
        if (!r.ok()) {
            return -1L;
        }
        return r.value() == null ? 0L : r.value().size();
    }

    /**
     * Background recovery: probes every node whose circuit is open and due, even if
     * no request routes to it, so recovery (and invalidation replay) does not depend
     * on traffic.
     */
    @Scheduled(fixedDelayString = "${queryflow.cache.circuit-breaker.probe-interval-ms:2000}")
    public void probeOpenNodes() {
        for (CacheNode node : ring.getNodes()) {
            if (state(node).breaker.isProbeDue()) {
                execute(node, t -> Boolean.TRUE);
            }
        }
    }

    // ------------------------------------------------------------- observability

    public List<CacheNodeStatus> getNodeStatuses() {
        List<CacheNodeStatus> out = new ArrayList<>();
        for (CacheNode node : ring.getNodes()) {
            NodeState s = state(node);
            out.add(CacheNodeStatus.builder()
                    .nodeId(node.getNodeId())
                    .host(node.getHost())
                    .port(node.getPort())
                    .state(s.breaker.getState())
                    .failures(s.breaker.getTotalFailures())
                    .shortCircuited(s.breaker.getShortCircuited())
                    .trips(s.breaker.getTrips())
                    .recoveries(s.breaker.getRecoveries())
                    .pendingInvalidations(s.pendingDeletes.size())
                    .flushOnRecovery(s.flushOnRecovery)
                    .build());
        }
        return out;
    }

    /** FULL when every circuit is closed, OFFLINE when none is, DEGRADED otherwise. */
    public String getCacheMode() {
        List<CacheNodeStatus> statuses = getNodeStatuses();
        long closed = statuses.stream().filter(s -> s.getState() == CircuitBreaker.State.CLOSED).count();
        if (statuses.isEmpty() || closed == 0) {
            return "OFFLINE";
        }
        return closed == statuses.size() ? "FULL" : "DEGRADED";
    }

    /** Reads answered without the cache because the owning node was unavailable. */
    public long getDegradedReads() {
        return degradedReads.get();
    }

    public long getShortCircuitedCalls() {
        return nodeStates.values().stream().mapToLong(s -> s.breaker.getShortCircuited()).sum();
    }

    public CircuitBreaker.State getState(CacheNode node) {
        return state(node).breaker.getState();
    }

    // ------------------------------------------------------------------ internals

    private NodeState state(CacheNode node) {
        return nodeStates.computeIfAbsent(node.getNodeId(), id -> new NodeState());
    }

    private void remember(CacheNode node, String key) {
        NodeState s = state(node);
        if (s.flushOnRecovery) {
            return;
        }
        s.pendingDeletes.add(key);
        if (s.pendingDeletes.size() > properties.getMaxPendingInvalidations()) {
            log.warn("Pending invalidations for {} exceeded {}; node will be flushed on recovery",
                    node.getNodeId(), properties.getMaxPendingInvalidations());
            s.flushOnRecovery = true;
            s.pendingDeletes.clear();
        }
    }

    private <T> Exec<T> execute(CacheNode node, Function<RedisTemplate<String, Object>, T> op) {
        NodeState s = state(node);
        CircuitBreaker.Permit permit = s.breaker.tryAcquire();
        if (permit == CircuitBreaker.Permit.DENIED) {
            return Exec.failed();
        }
        RedisTemplate<String, Object> template = node.getRedisTemplate();
        try {
            if (permit == CircuitBreaker.Permit.PROBE) {
                template.execute((RedisCallback<String>) RedisConnection::ping);
            }
            drainPending(node, s, template);
            T value = op.apply(template);
            boolean recovered = s.breaker.getState() != CircuitBreaker.State.CLOSED;
            s.breaker.onSuccess();
            if (recovered) {
                log.info("CACHE NODE RECOVERED node={}", node.getNodeId());
            }
            return new Exec<>(true, value);
        } catch (RuntimeException e) {
            CircuitBreaker.State before = s.breaker.getState();
            s.breaker.onFailure();
            CircuitBreaker.State after = s.breaker.getState();
            if (before != CircuitBreaker.State.OPEN && after == CircuitBreaker.State.OPEN) {
                log.warn("CACHE NODE CIRCUIT OPEN node={} cause={}", node.getNodeId(), e.getMessage());
            } else {
                log.warn("CACHE FALLBACK node={} cause={}", node.getNodeId(), e.getMessage());
            }
            return Exec.failed();
        }
    }

    /** Applies invalidations the node missed. Throws (and so fails the call) if Redis is still unreachable. */
    private void drainPending(CacheNode node, NodeState s, RedisTemplate<String, Object> template) {
        if (s.flushOnRecovery) {
            template.execute((RedisCallback<Object>) c -> {
                c.serverCommands().flushDb();
                return null;
            });
            s.flushOnRecovery = false;
            log.warn("CACHE NODE FLUSHED node={} (too many missed invalidations)", node.getNodeId());
        }
        if (s.pendingDeletes.isEmpty()) {
            return;
        }
        List<String> keys = new ArrayList<>(s.pendingDeletes);
        template.delete(keys);
        keys.forEach(s.pendingDeletes::remove);
        log.info("CACHE INVALIDATIONS REPLAYED node={} keys={}", node.getNodeId(), keys.size());
    }
}
