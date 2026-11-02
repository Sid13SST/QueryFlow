package com.queryflow.cache;

import com.queryflow.dto.CacheNode;
import com.queryflow.support.FakeRedisRing;
import com.queryflow.support.FakeRedisServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.queryflow.cache.ResilientCacheClient.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Lettuce client against three in-process fake Redis servers; the breaker
 * clock is faked so recovery timing is deterministic.
 */
class ResilientCacheClientTest {

    private FakeRedisRing ring;
    private final AtomicLong clock = new AtomicLong();
    private CacheResilienceProperties props;
    private ResilientCacheClient client;
    private final List<String> keys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ring = new FakeRedisRing();
        props = new CacheResilienceProperties();
        props.setFailureThreshold(3);
        props.setOpenDurationMs(1_000);
        client = new ResilientCacheClient(ring.ring, props, clock::get);
        for (int i = 0; i < 60; i++) {
            String key = "suggest:k" + i;
            keys.add(key);
            assertTrue(client.set(key, new ArrayList<>(List.of("v" + i)), 5, TimeUnit.MINUTES));
        }
    }

    @AfterEach
    void tearDown() {
        ring.close();
    }

    @Test
    void keysAreStoredOnTheirRingOwner() {
        for (String key : keys) {
            assertTrue(ring.serverFor(key).containsKey(key), key + " must live on its routed node");
        }
        // every physical node got a share
        ring.servers.forEach(s -> assertTrue(s.size() > 0));
    }

    @Test
    void losingOneNodeOnlyLosesThatNodesKeys() {
        CacheNode victim = ring.ring.getNodes().get(1);
        ring.serverFor(victim).stop();

        int hits = 0;
        int unavailable = 0;
        for (String key : keys) {
            ResilientCacheClient.CacheRead read = client.get(key);
            boolean ownedByVictim = ring.ring.route(key).equals(victim);
            if (ownedByVictim) {
                assertEquals(UNAVAILABLE, read.outcome(), key);
                unavailable++;
            } else {
                assertEquals(HIT, read.outcome(), key);
                hits++;
            }
        }
        assertTrue(unavailable > 0 && hits > 0);
        assertEquals(unavailable, client.getDegradedReads());
        assertEquals(CircuitBreaker.State.OPEN, client.getState(victim));
        assertEquals("DEGRADED", client.getCacheMode());
        // after the trip, the dead node is not even contacted
        assertTrue(client.getShortCircuitedCalls() >= unavailable - props.getFailureThreshold());
    }

    @Test
    void allNodesDownNeverThrowsAndReportsOffline() {
        ring.servers.forEach(FakeRedisServer::stop);
        for (String key : keys) {
            assertEquals(UNAVAILABLE, client.get(key).outcome());
            assertFalse(client.set(key, new ArrayList<>(List.of("x")), 1, TimeUnit.MINUTES));
        }
        assertEquals("OFFLINE", client.getCacheMode());
    }

    @Test
    void openCircuitAddsNoNetworkLatency() {
        FakeRedisServer server = ring.servers.get(0);
        CacheNode node = ring.ring.getNodes().get(0);
        String key = keys.stream().filter(k -> ring.ring.route(k).equals(node)).findFirst().orElseThrow();
        server.stop();
        for (int i = 0; i < props.getFailureThreshold(); i++) {
            client.get(key);
        }
        assertEquals(CircuitBreaker.State.OPEN, client.getState(node));

        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            assertEquals(UNAVAILABLE, client.get(key).outcome());
        }
        long avgMicros = (System.nanoTime() - start) / 1000 / 1000;
        assertTrue(avgMicros < 1000, "short-circuited reads should be ~microseconds, was " + avgMicros + "us");
    }

    @Test
    void nodeRecoversAutomaticallyAfterOpenDuration() throws Exception {
        CacheNode node = ring.ring.getNodes().get(2);
        FakeRedisServer server = ring.serverFor(node);
        List<String> owned = keys.stream().filter(k -> ring.ring.route(k).equals(node)).toList();

        server.stop();
        owned.forEach(client::get);
        assertEquals(CircuitBreaker.State.OPEN, client.getState(node));

        server.start(); // data kept: models a network partition healing
        clock.addAndGet(props.getOpenDurationMs());
        awaitClosed(node);

        for (String key : owned) {
            assertEquals(HIT, client.get(key).outcome(), key);
        }
        assertEquals("FULL", client.getCacheMode());
    }

    @Test
    void invalidationsMissedDuringOutageAreReplayedBeforeReads() throws Exception {
        CacheNode node = ring.ring.getNodes().get(0);
        FakeRedisServer server = ring.serverFor(node);
        String key = keys.stream().filter(k -> ring.ring.route(k).equals(node)).findFirst().orElseThrow();

        server.stop();
        assertFalse(client.delete(key), "delete cannot reach the node");
        assertTrue(server.containsKey(key), "node still holds the now-stale entry");

        server.start();
        clock.addAndGet(props.getOpenDurationMs());
        // The very first call to the healed node must not return the stale value.
        ResilientCacheClient.CacheRead read = awaitAvailable(key);
        assertEquals(MISS, read.outcome());
        assertFalse(server.containsKey(key));
        assertEquals(0, client.getNodeStatuses().get(0).getPendingInvalidations());
    }

    @Test
    void tooManyMissedInvalidationsFlushTheNodeOnRecovery() throws Exception {
        props.setMaxPendingInvalidations(2);
        CacheNode node = ring.ring.getNodes().get(1);
        FakeRedisServer server = ring.serverFor(node);
        List<String> owned = keys.stream().filter(k -> ring.ring.route(k).equals(node)).toList();
        assertTrue(owned.size() > 3);
        int otherNodesKeys = keys.size() - owned.size();

        server.stop();
        owned.subList(0, 3).forEach(client::delete);
        assertTrue(client.getNodeStatuses().get(1).isFlushOnRecovery());

        server.start();
        clock.addAndGet(props.getOpenDurationMs());
        awaitClosed(node);

        assertEquals(0, server.size(), "only the affected node is flushed");
        assertEquals(otherNodesKeys, ring.servers.get(0).size() + ring.servers.get(2).size());
    }

    @Test
    void undecodableValueIsAMissNotANodeFailure() {
        String key = keys.get(0);
        CacheNode node = ring.ring.route(key);
        // ImmutableCollections.ListN is written with type info Jackson cannot read back.
        client.set(key, List.of("a", "b", "c"), 1, TimeUnit.MINUTES);
        for (int i = 0; i < props.getFailureThreshold() + 2; i++) {
            assertEquals(MISS, client.get(key).outcome());
        }
        assertEquals(CircuitBreaker.State.CLOSED, client.getState(node));
        assertFalse(ring.serverFor(key).containsKey(key), "bad entry is evicted");
    }

    private void awaitClosed(CacheNode node) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (client.getState(node) != CircuitBreaker.State.CLOSED && System.currentTimeMillis() < deadline) {
            client.probeOpenNodes();
            if (client.getState(node) != CircuitBreaker.State.CLOSED) {
                // Lettuce reconnects in the background; give the next probe a fresh window.
                clock.addAndGet(props.getOpenDurationMs());
                Thread.sleep(50);
            }
        }
        assertEquals(CircuitBreaker.State.CLOSED, client.getState(node));
    }

    private ResilientCacheClient.CacheRead awaitAvailable(String key) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        ResilientCacheClient.CacheRead read = client.get(key);
        while (read.outcome() == UNAVAILABLE && System.currentTimeMillis() < deadline) {
            clock.addAndGet(props.getOpenDurationMs());
            Thread.sleep(50);
            read = client.get(key);
        }
        return read;
    }
}
