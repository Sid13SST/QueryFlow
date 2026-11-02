package com.queryflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryflow.cache.ResilientCacheClient;
import com.queryflow.dto.SuggestionResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SuggestionService {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private ResilientCacheClient cacheClient;

    @Autowired
    private CacheMetricsService cacheMetricsService;

    @Autowired
    private MetricsService metricsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<SuggestionResponse> getSuggestions(String prefix) {
        long startTime = System.currentTimeMillis();
        metricsService.incrementSuggestionRequests();
        
        try {
            if (prefix == null || prefix.trim().isEmpty()) {
                throw new IllegalArgumentException("Query prefix cannot be empty");
            }

            String normalizedPrefix = prefix.trim().toLowerCase();
            log.info("Prefix received: '{}'", normalizedPrefix);

            String cacheKey = "suggest:" + normalizedPrefix;
            ResilientCacheClient.CacheRead cached = cacheClient.get(cacheKey);
            if (cached.isHit() && cached.value() instanceof List<?> cachedList) {
                log.info("CACHE HIT prefix={}", normalizedPrefix);
                cacheMetricsService.incrementHits();
                return cachedList.stream()
                        .map(item -> objectMapper.convertValue(item, SuggestionResponse.class))
                        .collect(Collectors.toList());
            }
            if (cached.outcome() == ResilientCacheClient.Outcome.UNAVAILABLE) {
                log.warn("CACHE UNAVAILABLE prefix={} - serving from PostgreSQL", normalizedPrefix);
            }

            log.info("CACHE MISS prefix={}", normalizedPrefix);
            cacheMetricsService.incrementMisses();
            metricsService.incrementDatabaseReads();

            // Query database (case-insensitive query starting with prefix)
            List<SearchQuery> queries = searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(normalizedPrefix);
            
            log.info("Number of results returned from database: {}", queries.size());

            List<SuggestionResponse> suggestions = queries.stream()
                    .map(q -> new SuggestionResponse(q.getQuery(), q.getCount()))
                    .collect(Collectors.toList());

            if (cached.outcome() != ResilientCacheClient.Outcome.UNAVAILABLE) {
                cacheClient.set(cacheKey, suggestions, 5, TimeUnit.MINUTES);
            }

            return suggestions;
        } finally {
            metricsService.recordSuggestionLatency(System.currentTimeMillis() - startTime);
        }
    }
}

