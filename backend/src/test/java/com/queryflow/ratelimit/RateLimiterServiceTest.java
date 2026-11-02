package com.queryflow.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private static final long SECOND = 1_000_000_000L;
    private final AtomicLong clock = new AtomicLong();
    private RateLimitProperties props;
    private RateLimiterService limiter;

    @BeforeEach
    void setUp() {
        props = new RateLimitProperties();
        props.getEndpoints().put("suggest", RateLimitProperties.Rule.of(2, 1.0, "/suggest"));
        props.getEndpoints().put("trending", RateLimitProperties.Rule.of(5, 1.0, "/trending", "/trending/**"));
        limiter = new RateLimiterService(props, clock::get);
    }

    @Test
    void matchesConfiguredPathPatterns() {
        assertEquals("suggest", limiter.matchRule("/suggest").orElseThrow());
        assertEquals("trending", limiter.matchRule("/trending").orElseThrow());
        assertEquals("trending", limiter.matchRule("/trending/explain").orElseThrow());
        assertTrue(limiter.matchRule("/metrics").isEmpty());
        assertTrue(limiter.matchRule("/health").isEmpty());
    }

    @Test
    void bucketsAreIsolatedPerClientAndPerEndpoint() {
        assertTrue(limiter.tryAcquire("suggest", "10.0.0.1").allowed());
        assertTrue(limiter.tryAcquire("suggest", "10.0.0.1").allowed());
        assertFalse(limiter.tryAcquire("suggest", "10.0.0.1").allowed());

        assertTrue(limiter.tryAcquire("suggest", "10.0.0.2").allowed(), "other IPs keep their own budget");
        assertTrue(limiter.tryAcquire("trending", "10.0.0.1").allowed(), "other endpoints keep their own budget");
    }

    @Test
    void countersTrackAllowedAndRejectedPerRule() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("suggest", "10.0.0.1");
        }
        assertEquals(2, limiter.getTotalAllowed());
        assertEquals(3, limiter.getTotalRejected());
        assertEquals(2L, limiter.getCountersByRule().get("suggest").get("allowed"));
        assertEquals(3L, limiter.getCountersByRule().get("suggest").get("rejected"));
        assertEquals(0L, limiter.getCountersByRule().get("trending").get("rejected"));
    }

    @Test
    void idleBucketsAreEvicted() {
        limiter.tryAcquire("suggest", "10.0.0.1");
        limiter.tryAcquire("suggest", "10.0.0.2");
        assertEquals(2, limiter.getTrackedBuckets());

        clock.addAndGet((props.getIdleBucketTtlSeconds() + 1) * SECOND);
        limiter.tryAcquire("suggest", "10.0.0.2");
        assertEquals(1, limiter.evictIdle());
        assertEquals(1, limiter.getTrackedBuckets());
    }

    @Test
    void failsOpenWhenBucketTableIsFull() {
        props.setMaxTrackedBuckets(1);
        limiter.tryAcquire("suggest", "10.0.0.1");
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("suggest", "10.0.0.99").allowed());
        }
        assertEquals(5, limiter.getUntracked());
        assertEquals(1, limiter.getTrackedBuckets());
    }

    @Test
    void invalidRuleFailsFastAtStartup() {
        RateLimitProperties bad = new RateLimitProperties();
        bad.getEndpoints().put("suggest", RateLimitProperties.Rule.of(0, 1.0, "/suggest"));
        assertThrows(IllegalStateException.class, () -> new RateLimiterService(bad));
    }
}
