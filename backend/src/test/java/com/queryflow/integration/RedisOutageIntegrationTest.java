package com.queryflow.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryflow.cache.CircuitBreaker;
import com.queryflow.cache.ResilientCacheClient;
import com.queryflow.dto.CacheNode;
import com.queryflow.repository.SearchQueryRepository;
import com.queryflow.service.BatchWriterService;
import com.queryflow.service.CacheMetricsService;
import com.queryflow.service.ConsistentHashService;
import com.queryflow.support.FakeRedisServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whole application, real HTTP layer, real Lettuce client, three fake Redis nodes
 * that can be stopped and started. Proves:
 * <ol>
 *   <li>with every Redis node down, /suggest still returns exactly what PostgreSQL holds;</li>
 *   <li>with one node down, only that node's keys miss;</li>
 *   <li>when Redis returns, the hit rate recovers without a restart;</li>
 *   <li>invalidations missed during a partition are not lost.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
class RedisOutageIntegrationTest {

    private static final List<String> PREFIXES = List.of(
            "i", "ip", "iph", "iphone", "iphone 1", "j", "ja", "java", "p", "py", "po",
            "r", "re", "t", "ty", "s", "sp", "d", "do", "k", "ku");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        FullStackTestSupport.register(registry, "outage");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private SearchQueryRepository repository;
    @Autowired
    private CacheMetricsService cacheMetrics;
    @Autowired
    private ResilientCacheClient cacheClient;
    @Autowired
    private ConsistentHashService ring;
    @Autowired
    private BatchWriterService batchWriter;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void healthyEmptyCache() throws Exception {
        FullStackTestSupport.startAll();
        FullStackTestSupport.REDIS.forEach(FakeRedisServer::clear);
        awaitCacheMode("FULL");
    }

    @Test
    void warmCacheServesRepeatedPrefixesFromRedis() throws Exception {
        long hits0 = cacheMetrics.getStats().getHits();
        suggestAllAndVerify(); // fills the cache
        long hits1 = cacheMetrics.getStats().getHits();
        suggestAllAndVerify();
        assertEquals(PREFIXES.size(), cacheMetrics.getStats().getHits() - hits1, "second pass is 100% hits");
        assertEquals(hits0, hits1, "first pass on an empty cache has no hits");
    }

    @Test
    void allRedisNodesDownStillServesCorrectSuggestionsAndRecovers() throws Exception {
        suggestAllAndVerify();

        FullStackTestSupport.stopAll();
        long errorsBefore = cacheClient.getDegradedReads();
        for (int round = 0; round < 3; round++) {
            suggestAllAndVerify(); // every response equals PostgreSQL's answer
        }
        assertEquals(3L * PREFIXES.size(), cacheClient.getDegradedReads() - errorsBefore);
        assertEquals("OFFLINE", health().get("cacheMode"));

        // Nodes come back empty (as after a Redis restart).
        FullStackTestSupport.REDIS.forEach(FakeRedisServer::clear);
        FullStackTestSupport.startAll();
        awaitCacheMode("FULL");

        suggestAllAndVerify(); // repopulate
        long hitsBefore = cacheMetrics.getStats().getHits();
        long missesBefore = cacheMetrics.getStats().getMisses();
        suggestAllAndVerify();
        long hits = cacheMetrics.getStats().getHits() - hitsBefore;
        long misses = cacheMetrics.getStats().getMisses() - missesBefore;
        assertEquals(PREFIXES.size(), hits);
        assertEquals(0, misses, "hit rate back to 100% for a repeated workload");
    }

    @Test
    void oneNodeDownOnlyLosesThatNodesKeys() throws Exception {
        suggestAllAndVerify();
        CacheNode victim = ring.route("suggest:java");
        FakeRedisServer victimServer = FullStackTestSupport.REDIS.get(ring.getNodes().indexOf(victim));
        victimServer.stop();

        long expectedHits = PREFIXES.stream()
                .filter(p -> !ring.route("suggest:" + p).equals(victim)).count();
        assertTrue(expectedHits > 0 && expectedHits < PREFIXES.size());

        long hitsBefore = cacheMetrics.getStats().getHits();
        suggestAllAndVerify();
        assertEquals(expectedHits, cacheMetrics.getStats().getHits() - hitsBefore,
                "keys on healthy nodes keep hitting");
        assertEquals("DEGRADED", health().get("cacheMode"));
    }

    @Test
    void invalidationMissedDuringPartitionIsAppliedOnRecovery() throws Exception {
        assertEquals(countOf("java"), suggest("java").get("java")); // cached now
        CacheNode owner = ring.route("suggest:java");
        FakeRedisServer ownerServer = FullStackTestSupport.REDIS.get(ring.getNodes().indexOf(owner));

        ownerServer.stop(); // partition: the node keeps its (soon stale) data
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"java\"}"))
                    .andExpect(status().isOk());
        }
        long before = countOf("java");
        batchWriter.scheduleFlush();
        assertEquals(before + 5, countOf("java"));
        assertTrue(ownerServer.containsKey("suggest:java"), "stale entry still on the partitioned node");

        // Keep reading until the owner's circuit has tripped, so recovery must go through
        // a probe instead of a lucky failed read falling back to PostgreSQL.
        for (int i = 0; i < 10 && cacheClient.getState(owner) != CircuitBreaker.State.OPEN; i++) {
            assertEquals(before + 5, suggest("java").get("java"));
        }
        assertEquals(CircuitBreaker.State.OPEN, cacheClient.getState(owner));

        ownerServer.start();
        awaitCacheMode("FULL"); // a successful probe => the node is reachable again
        assertFalse(ownerServer.containsKey("suggest:java"), "deferred DEL replayed on recovery");
        long hitsBefore = cacheMetrics.getStats().getHits();
        assertEquals(before + 5, suggest("java").get("java"), "no stale count after the node returns");
        assertEquals(before + 5, suggest("java").get("java"));
        assertEquals(1, cacheMetrics.getStats().getHits() - hitsBefore, "fresh value cached again on the owner");
    }

    // ------------------------------------------------------------------ helpers

    private void suggestAllAndVerify() throws Exception {
        for (String prefix : PREFIXES) {
            Map<String, Long> actual = suggest(prefix);
            Map<String, Long> expected = repository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(prefix)
                    .stream().collect(Collectors.toMap(q -> q.getQuery(), q -> q.getCount(),
                            (a, b) -> a, LinkedHashMap::new));
            assertFalse(expected.isEmpty(), "dataset must cover prefix " + prefix);
            assertEquals(expected, actual, "suggestions for '" + prefix + "'");
        }
    }

    private Map<String, Long> suggest(String prefix) throws Exception {
        MvcResult r = mvc.perform(get("/suggest").param("q", prefix)).andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> body = json.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {
        });
        Map<String, Long> out = new LinkedHashMap<>();
        body.forEach(m -> out.put((String) m.get("query"), ((Number) m.get("count")).longValue()));
        return out;
    }

    private long countOf(String query) {
        return repository.findByQuery(query).orElseThrow().getCount();
    }

    private Map<String, Object> health() throws Exception {
        MvcResult r = mvc.perform(get("/health")).andExpect(status().isOk()).andReturn();
        return json.readValue(r.getResponse().getContentAsString(), new TypeReference<>() {
        });
    }

    private void awaitCacheMode(String mode) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!mode.equals(cacheClient.getCacheMode()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(mode, cacheClient.getCacheMode());
    }
}
