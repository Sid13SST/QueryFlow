package com.queryflow.integration;

import com.queryflow.repository.SearchQueryRepository;
import com.queryflow.service.BatchWriterService;
import com.queryflow.service.CacheInvalidationService;
import com.queryflow.service.MetricsService;
import com.queryflow.service.SearchBufferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two batch writers with their own in-memory buffers (what two backend instances
 * are) flush into the same database at the same moments, many times over. Every
 * buffered search must end up counted exactly once: no lost updates from
 * concurrent increments, no double counting when a flush is retried after a
 * conflicting insert of a brand-new query.
 *
 * Runs on H2 by default; set QUERYFLOW_TEST_JDBC_URL to run it on PostgreSQL.
 */
@SpringBootTest
class MultiInstanceBatchWriteTest {

    private static final int ROUNDS = 40;
    private static final List<String> EXISTING = List.of("java", "python", "docker");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        FullStackTestSupport.register(registry, "multi");
    }

    @Autowired
    private SearchQueryRepository repository;
    @Autowired
    private PlatformTransactionManager txManager;
    @Autowired
    private CacheInvalidationService cacheInvalidationService;
    @Autowired
    private MetricsService metricsService;

    @Test
    void concurrentIncrementsOfExistingQueriesAreNeverLost() throws Exception {
        Map<String, Long> before = counts();
        SearchBufferService bufferA = new SearchBufferService();
        SearchBufferService bufferB = new SearchBufferService();

        runConcurrently(writer(bufferA), bufferA, writer(bufferB), bufferB, false);

        assertEquals(0, bufferA.getPendingEvents());
        assertEquals(0, bufferB.getPendingEvents());
        for (String q : EXISTING) {
            assertEquals(before.get(q) + (long) ROUNDS * (WEIGHT_A + WEIGHT_B), countOf(q), "count for " + q);
        }
    }

    @Test
    void racingInsertsOfANewQueryAreCountedExactlyOnce() throws Exception {
        Map<String, Long> before = counts();
        SearchBufferService bufferA = new SearchBufferService();
        SearchBufferService bufferB = new SearchBufferService();
        BatchWriterService a = writer(bufferA);
        BatchWriterService b = writer(bufferB);

        runConcurrently(a, bufferA, b, bufferB, true);

        // A flush that lost an insert race rolled back as a whole and re-buffered its
        // snapshot; the next flush applies it via the UPDATE path. Drain those.
        for (int i = 0; i < 10 && (bufferA.getPendingEvents() > 0 || bufferB.getPendingEvents() > 0); i++) {
            a.scheduleFlush();
            b.scheduleFlush();
        }
        assertEquals(0, bufferA.getPendingEvents());
        assertEquals(0, bufferB.getPendingEvents());

        for (String q : EXISTING) {
            assertEquals(before.get(q) + (long) ROUNDS * (WEIGHT_A + WEIGHT_B), countOf(q), "count for " + q);
        }
        for (int round = 0; round < ROUNDS; round++) {
            assertEquals(2L, countOf(NEW_PREFIX + round),
                    "new query inserted once and incremented by both instances");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static final int WEIGHT_A = 3;
    private static final int WEIGHT_B = 5;
    private static final String NEW_PREFIX = "brand new " + System.nanoTime() + " ";

    /**
     * Each round, each instance buffers WEIGHT searches of every existing query (and
     * optionally one search of a query new to the database), then both flush at the
     * same instant.
     */
    private void runConcurrently(BatchWriterService a, SearchBufferService bufferA,
                                 BatchWriterService b, SearchBufferService bufferB,
                                 boolean withNewQuery) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> fa = pool.submit(() -> run(a, bufferA, WEIGHT_A, withNewQuery, barrier));
            Future<?> fb = pool.submit(() -> run(b, bufferB, WEIGHT_B, withNewQuery, barrier));
            fa.get(120, TimeUnit.SECONDS);
            fb.get(120, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private void run(BatchWriterService writer, SearchBufferService buffer, int weight, boolean withNewQuery,
                     CyclicBarrier barrier) {
        try {
            for (int round = 0; round < ROUNDS; round++) {
                for (String q : EXISTING) {
                    for (int i = 0; i < weight; i++) {
                        buffer.increment(q);
                    }
                }
                if (withNewQuery) {
                    buffer.increment(NEW_PREFIX + round);
                }
                barrier.await(30, TimeUnit.SECONDS);
                writer.scheduleFlush();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Long> counts() {
        Map<String, Long> m = new HashMap<>();
        EXISTING.forEach(q -> m.put(q, countOf(q)));
        return m;
    }

    private long countOf(String q) {
        return repository.findByQuery(q).orElseThrow().getCount();
    }

    private BatchWriterService writer(SearchBufferService buffer) {
        BatchWriterService w = new BatchWriterService();
        ReflectionTestUtils.setField(w, "searchBufferService", buffer);
        ReflectionTestUtils.setField(w, "searchQueryRepository", repository);
        ReflectionTestUtils.setField(w, "cacheInvalidationService", cacheInvalidationService);
        ReflectionTestUtils.setField(w, "metricsService", metricsService);
        w.setTransactionManager(txManager);
        return w;
    }
}
