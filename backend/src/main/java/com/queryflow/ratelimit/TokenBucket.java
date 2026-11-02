package com.queryflow.ratelimit;

/**
 * Classic token bucket with continuous refill.
 *
 * The bucket starts full ({@code capacity} tokens) so a client can burst up to
 * {@code capacity} requests, after which it is held to {@code refillPerSecond}
 * requests per second on average. Time is passed in explicitly (nanoseconds) so
 * the bucket is deterministic under test.
 *
 * Thread-safety: all state changes happen inside {@code synchronized} methods.
 * Contention is per client+endpoint, so a single lock per bucket is cheap.
 */
public class TokenBucket {

    private final long capacity;
    private final double refillPerNano;
    private double tokens;
    private long lastRefillNanos;
    private volatile long lastAccessNanos;

    public TokenBucket(long capacity, double refillPerSecond, long nowNanos) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be > 0");
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000.0;
        this.tokens = capacity;
        this.lastRefillNanos = nowNanos;
        this.lastAccessNanos = nowNanos;
    }

    /**
     * Attempts to take one token.
     *
     * @return the outcome, including how many whole tokens remain and, when
     *         rejected, how long until the next token is available
     */
    public synchronized Result tryConsume(long nowNanos) {
        refill(nowNanos);
        lastAccessNanos = nowNanos;
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return new Result(true, (long) Math.floor(tokens), 0L);
        }
        double missing = 1.0 - tokens;
        long waitNanos = (long) Math.ceil(missing / refillPerNano);
        return new Result(false, 0L, waitNanos);
    }

    /** True when the bucket has been idle long enough to be full again and can be dropped. */
    public boolean isIdleSince(long nowNanos, long idleNanos) {
        return nowNanos - lastAccessNanos >= idleNanos;
    }

    public long getCapacity() {
        return capacity;
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
            lastRefillNanos = nowNanos;
        }
    }

    /**
     * @param allowed        whether the request may proceed
     * @param remaining      whole tokens left after this request
     * @param retryAfterNanos when rejected, time until one token is available
     */
    public record Result(boolean allowed, long remaining, long retryAfterNanos) {

        /** Retry-After header value: whole seconds, rounded up, never below 1. */
        public long retryAfterSeconds() {
            if (allowed) {
                return 0L;
            }
            return Math.max(1L, (retryAfterNanos + 999_999_999L) / 1_000_000_000L);
        }
    }
}
