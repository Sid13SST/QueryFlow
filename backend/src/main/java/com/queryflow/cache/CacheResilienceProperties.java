package com.queryflow.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings bound from {@code queryflow.cache.circuit-breaker.*}. */
@Data
@ConfigurationProperties(prefix = "queryflow.cache.circuit-breaker")
public class CacheResilienceProperties {

    /** Consecutive failed Redis calls on one node before its circuit opens. */
    private int failureThreshold = 3;

    /** How long an open circuit skips the node before a single probe is allowed. */
    private long openDurationMs = 5_000;

    /** How often the background prober checks open circuits whose wait has elapsed. */
    private long probeIntervalMs = 2_000;

    /**
     * Invalidations (DELs) that could not reach a node are remembered and replayed
     * before the node serves reads again. Beyond this many keys the node is flushed
     * on recovery instead (it only holds its own share of the cache).
     */
    private int maxPendingInvalidations = 10_000;
}
