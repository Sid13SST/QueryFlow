package com.queryflow.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        TokenBucket bucket = new TokenBucket(3, 1.0, 0L);

        assertTrue(bucket.tryConsume(0L).allowed());
        assertTrue(bucket.tryConsume(0L).allowed());
        TokenBucket.Result third = bucket.tryConsume(0L);
        assertTrue(third.allowed());
        assertEquals(0, third.remaining());

        TokenBucket.Result fourth = bucket.tryConsume(0L);
        assertFalse(fourth.allowed());
        assertEquals(1, fourth.retryAfterSeconds());
    }

    @Test
    void refillsContinuouslyAtConfiguredRate() {
        TokenBucket bucket = new TokenBucket(2, 4.0, 0L); // one token every 250 ms
        bucket.tryConsume(0L);
        bucket.tryConsume(0L);
        assertFalse(bucket.tryConsume(0L).allowed());

        assertFalse(bucket.tryConsume(SECOND / 8).allowed(), "only half a token after 125 ms");
        assertTrue(bucket.tryConsume(SECOND / 4).allowed(), "a full token after 250 ms");
    }

    @Test
    void neverExceedsCapacityAfterLongIdle() {
        TokenBucket bucket = new TokenBucket(2, 100.0, 0L);
        long later = 3600 * SECOND;
        assertTrue(bucket.tryConsume(later).allowed());
        assertTrue(bucket.tryConsume(later).allowed());
        assertFalse(bucket.tryConsume(later).allowed());
    }

    @Test
    void retryAfterRoundsUpToWholeSeconds() {
        TokenBucket bucket = new TokenBucket(1, 0.2, 0L); // one token every 5 s
        bucket.tryConsume(0L);
        TokenBucket.Result rejected = bucket.tryConsume(SECOND / 2); // 4.5 s to go
        assertFalse(rejected.allowed());
        assertEquals(5, rejected.retryAfterSeconds());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1.0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, 0.0, 0L));
    }
}
