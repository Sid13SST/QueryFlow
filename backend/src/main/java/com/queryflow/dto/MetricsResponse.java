package com.queryflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResponse {
    /** Which backend produced these numbers. All counters below are for this instance only. */
    private String instanceId;
    private long suggestionRequests;
    private long trendingRequests;
    private long cacheHits;
    private long cacheMisses;
    private double cacheHitRate;
    private long databaseReads;
    private long databaseWrites;
    private long batchFlushes;
    private double averageFlushDurationMs;
    private long cacheInvalidations;

    // Cache resilience
    private String cacheMode;
    private long cacheDegradedReads;
    private long cacheShortCircuitedCalls;
    private long cacheDeferredInvalidations;
    private Map<String, String> cacheNodeStates;

    // Rate limiting (instance-local, see docs/ARCHITECTURE.md)
    private long rateLimitAllowed;
    private long rateLimitRejected;
    private long rateLimitTrackedBuckets;
    private Map<String, Map<String, Long>> rateLimitByEndpoint;
}
