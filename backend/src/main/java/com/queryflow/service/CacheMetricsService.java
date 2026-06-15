package com.queryflow.service;

import com.queryflow.dto.CacheStatsResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class CacheMetricsService {

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    public void incrementHits() {
        cacheHits.incrementAndGet();
    }

    public void incrementMisses() {
        cacheMisses.incrementAndGet();
    }

    public CacheStatsResponse getStats() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = 0.0;
        
        long total = hits + misses;
        if (total > 0) {
            double rate = (hits * 100.0) / total;
            // Round to 1 decimal place, e.g. 72.7
            hitRate = Math.round(rate * 10.0) / 10.0;
        }
        
        return CacheStatsResponse.builder()
                .hits(hits)
                .misses(misses)
                .hitRate(hitRate)
                .build();
    }

    public void reset() {
        cacheHits.set(0);
        cacheMisses.set(0);
    }
}
