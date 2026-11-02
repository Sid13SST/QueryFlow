package com.queryflow.controller;

import com.queryflow.dto.MetricsLatencyResponse;
import com.queryflow.dto.MetricsResponse;
import com.queryflow.cache.CacheNodeStatus;
import com.queryflow.cache.ResilientCacheClient;
import com.queryflow.ratelimit.RateLimiterService;
import com.queryflow.service.CacheInvalidationService;
import com.queryflow.service.MetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.TreeMap;
import java.util.stream.Collectors;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class MetricsController {

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private ResilientCacheClient cacheClient;

    @Autowired
    private CacheInvalidationService cacheInvalidationService;

    @GetMapping("/metrics")
    public ResponseEntity<MetricsResponse> getMetrics() {
        log.info("GET /metrics requested");
        MetricsResponse response = MetricsResponse.builder()
                .suggestionRequests(metricsService.getSuggestionRequests())
                .trendingRequests(metricsService.getTrendingRequests())
                .cacheHits(metricsService.getCacheHits())
                .cacheMisses(metricsService.getCacheMisses())
                .cacheHitRate(metricsService.getCacheHitRate())
                .databaseReads(metricsService.getDatabaseReads())
                .databaseWrites(metricsService.getDatabaseWrites())
                .batchFlushes(metricsService.getBatchFlushCount())
                .averageFlushDurationMs(metricsService.getAverageFlushDurationMs())
                .cacheInvalidations(metricsService.getCacheInvalidations())
                .cacheMode(cacheClient.getCacheMode())
                .cacheDegradedReads(cacheClient.getDegradedReads())
                .cacheShortCircuitedCalls(cacheClient.getShortCircuitedCalls())
                .cacheDeferredInvalidations(cacheInvalidationService.getDeferredInvalidationCount())
                .cacheNodeStates(cacheClient.getNodeStatuses().stream().collect(Collectors.toMap(
                        CacheNodeStatus::getNodeId, s -> s.getState().name(), (a, b) -> a, TreeMap::new)))
                .rateLimitAllowed(rateLimiterService.getTotalAllowed())
                .rateLimitRejected(rateLimiterService.getTotalRejected())
                .rateLimitTrackedBuckets(rateLimiterService.getTrackedBuckets())
                .rateLimitByEndpoint(rateLimiterService.getCountersByRule())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/metrics/latency")
    public ResponseEntity<MetricsLatencyResponse> getLatency() {
        log.info("GET /metrics/latency requested");
        MetricsLatencyResponse response = MetricsLatencyResponse.builder()
                .suggestionAvgMs(metricsService.getSuggestionAvgMs())
                .suggestionMaxMs(metricsService.getSuggestionMaxMs())
                .trendingAvgMs(metricsService.getTrendingAvgMs())
                .trendingMaxMs(metricsService.getTrendingMaxMs())
                .build();
        return ResponseEntity.ok(response);
    }
}
