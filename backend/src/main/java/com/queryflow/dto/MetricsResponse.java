package com.queryflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResponse {
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
}
