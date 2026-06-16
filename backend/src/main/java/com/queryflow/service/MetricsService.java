package com.queryflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    @Autowired
    private CacheMetricsService cacheMetricsService;

    @Autowired
    private CacheInvalidationService cacheInvalidationService;

    private final AtomicLong suggestionRequests = new AtomicLong(0);
    private final AtomicLong trendingRequests = new AtomicLong(0);
    private final AtomicLong databaseReads = new AtomicLong(0);
    private final AtomicLong databaseWrites = new AtomicLong(0);
    private final AtomicLong totalSearches = new AtomicLong(0);

    private final AtomicLong batchFlushCount = new AtomicLong(0);
    private final AtomicLong batchFlushDurationSum = new AtomicLong(0);
    private final AtomicLong lastFlushDuration = new AtomicLong(0);

    // Latency Suggestion
    private final AtomicLong suggestionLatencySum = new AtomicLong(0);
    private final AtomicLong suggestionLatencyCount = new AtomicLong(0);
    private final AtomicLong suggestionMaxLatency = new AtomicLong(0);

    // Latency Trending
    private final AtomicLong trendingLatencySum = new AtomicLong(0);
    private final AtomicLong trendingLatencyCount = new AtomicLong(0);
    private final AtomicLong trendingMaxLatency = new AtomicLong(0);

    public void incrementSuggestionRequests() {
        suggestionRequests.incrementAndGet();
    }

    public void incrementTrendingRequests() {
        trendingRequests.incrementAndGet();
    }

    public void incrementDatabaseReads() {
        databaseReads.incrementAndGet();
    }

    public void incrementDatabaseWrites() {
        databaseWrites.incrementAndGet();
    }

    public void incrementTotalSearches() {
        totalSearches.incrementAndGet();
    }

    public void recordBatchFlush(long durationMs) {
        batchFlushCount.incrementAndGet();
        batchFlushDurationSum.addAndGet(durationMs);
        lastFlushDuration.set(durationMs);
    }

    public void recordSuggestionLatency(long durationMs) {
        suggestionLatencySum.addAndGet(durationMs);
        suggestionLatencyCount.incrementAndGet();
        suggestionMaxLatency.accumulateAndGet(durationMs, Math::max);
    }

    public void recordTrendingLatency(long durationMs) {
        trendingLatencySum.addAndGet(durationMs);
        trendingLatencyCount.incrementAndGet();
        trendingMaxLatency.accumulateAndGet(durationMs, Math::max);
    }

    public long getSuggestionRequests() {
        return suggestionRequests.get();
    }

    public long getTrendingRequests() {
        return trendingRequests.get();
    }

    public long getDatabaseReads() {
        return databaseReads.get();
    }

    public long getDatabaseWrites() {
        return databaseWrites.get();
    }

    public long getTotalSearches() {
        return totalSearches.get();
    }

    public long getCacheHits() {
        return cacheMetricsService.getStats().getHits();
    }

    public long getCacheMisses() {
        return cacheMetricsService.getStats().getMisses();
    }

    public double getCacheHitRate() {
        return cacheMetricsService.getStats().getHitRate();
    }

    public long getBatchFlushCount() {
        return batchFlushCount.get();
    }

    public double getAverageFlushDurationMs() {
        long count = batchFlushCount.get();
        if (count == 0) return 0.0;
        double avg = (double) batchFlushDurationSum.get() / count;
        return Math.round(avg * 100.0) / 100.0;
    }

    public long getLastFlushDurationMs() {
        return lastFlushDuration.get();
    }

    public long getCacheInvalidations() {
        return cacheInvalidationService.getInvalidationCount();
    }

    public double getSuggestionAvgMs() {
        long count = suggestionLatencyCount.get();
        if (count == 0) return 0.0;
        double avg = (double) suggestionLatencySum.get() / count;
        return Math.round(avg * 100.0) / 100.0;
    }

    public long getSuggestionMaxMs() {
        return suggestionMaxLatency.get();
    }

    public double getTrendingAvgMs() {
        long count = trendingLatencyCount.get();
        if (count == 0) return 0.0;
        double avg = (double) trendingLatencySum.get() / count;
        return Math.round(avg * 100.0) / 100.0;
    }

    public long getTrendingMaxMs() {
        return trendingMaxLatency.get();
    }

    public void reset() {
        suggestionRequests.set(0);
        trendingRequests.set(0);
        databaseReads.set(0);
        databaseWrites.set(0);
        totalSearches.set(0);
        batchFlushCount.set(0);
        batchFlushDurationSum.set(0);
        lastFlushDuration.set(0);
        suggestionLatencySum.set(0);
        suggestionLatencyCount.set(0);
        suggestionMaxLatency.set(0);
        trendingLatencySum.set(0);
        trendingLatencyCount.set(0);
        trendingMaxLatency.set(0);
        cacheMetricsService.reset();
    }
}
