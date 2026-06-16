package com.queryflow.controller;

import com.queryflow.dto.DemoSummaryResponse;
import com.queryflow.repository.SearchQueryRepository;
import com.queryflow.service.ConsistentHashService;
import com.queryflow.service.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class DemoController {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private ConsistentHashService consistentHashService;

    @Autowired
    private MetricsService metricsService;

    @GetMapping("/demo/summary")
    public ResponseEntity<DemoSummaryResponse> getSummary() {
        log.info("GET /demo/summary requested");
        
        long datasetQueries = 0;
        try {
            datasetQueries = searchQueryRepository.count();
        } catch (Exception e) {
            log.error("Failed to fetch query count from DB: {}", e.getMessage());
        }

        int cacheNodes = consistentHashService.getNodes().size();
        double cacheHitRate = metricsService.getCacheHitRate();
        long batchFlushes = metricsService.getBatchFlushCount();
        int trendingQueries = 10; // Top 10 queries returned by trending endpoint

        DemoSummaryResponse response = DemoSummaryResponse.builder()
                .datasetQueries(datasetQueries)
                .cacheNodes(cacheNodes)
                .cacheHitRate(cacheHitRate)
                .batchFlushes(batchFlushes)
                .trendingQueries(trendingQueries)
                .systemStatus("HEALTHY")
                .build();

        return ResponseEntity.ok(response);
    }
}
