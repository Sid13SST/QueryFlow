package com.queryflow.service;

import com.queryflow.dto.BenchmarkReportResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BenchmarkService {

    @Autowired
    private MetricsService metricsService;

    public BenchmarkReportResponse generateReport() {
        double hitRate = metricsService.getCacheHitRate();
        long savedReads = metricsService.getCacheHits();
        long batchFlushes = metricsService.getBatchFlushCount();

        long totalSearches = metricsService.getTotalSearches();
        long dbWrites = metricsService.getDatabaseWrites();

        double writeReduction = 0.0;
        if (totalSearches > 0) {
            double rawReduction = ((double) (totalSearches - dbWrites) / totalSearches) * 100.0;
            // Bound it to [0, 100]
            rawReduction = Math.max(0.0, Math.min(100.0, rawReduction));
            writeReduction = Math.round(rawReduction * 10.0) / 10.0;
        }

        return BenchmarkReportResponse.builder()
                .cacheHitRate(hitRate)
                .estimatedDbReadsSaved(savedReads)
                .estimatedWriteReduction(writeReduction)
                .batchFlushes(batchFlushes)
                .build();
    }
}
