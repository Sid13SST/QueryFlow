package com.queryflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryflow.dto.CacheNode;
import com.queryflow.dto.SuggestionResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SuggestionService {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private ConsistentHashService consistentHashService;

    @Autowired
    private CacheMetricsService cacheMetricsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public List<SuggestionResponse> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Query prefix cannot be empty");
        }

        String normalizedPrefix = prefix.trim().toLowerCase();
        log.info("Prefix received: '{}'", normalizedPrefix);

        String cacheKey = "suggest:" + normalizedPrefix;
        CacheNode targetNode = consistentHashService.route(cacheKey);
        
        if (targetNode != null) {
            log.info("prefix={} routedTo={}", normalizedPrefix, targetNode.getNodeId());
            RedisTemplate<String, Object> redisTemplate = targetNode.getRedisTemplate();
            try {
                Object cachedObj = redisTemplate.opsForValue().get(cacheKey);
                if (cachedObj instanceof List) {
                    log.info("CACHE HIT prefix={}", normalizedPrefix);
                    cacheMetricsService.incrementHits();
                    
                    List<?> cachedList = (List<?>) cachedObj;
                    return cachedList.stream()
                            .map(item -> objectMapper.convertValue(item, SuggestionResponse.class))
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("CACHE FALLBACK: Error reading from Redis node {}: {}", targetNode.getNodeId(), e.getMessage());
            }
        } else {
            log.warn("CACHE FALLBACK: No cache nodes available in consistent hash ring");
        }

        log.info("CACHE MISS prefix={}", normalizedPrefix);
        cacheMetricsService.incrementMisses();

        // Query database (case-insensitive query starting with prefix)
        List<SearchQuery> queries = searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(normalizedPrefix);
        
        log.info("Number of results returned from database: {}", queries.size());

        List<SuggestionResponse> suggestions = queries.stream()
                .map(q -> new SuggestionResponse(q.getQuery(), q.getCount()))
                .collect(Collectors.toList());

        if (targetNode != null) {
            RedisTemplate<String, Object> redisTemplate = targetNode.getRedisTemplate();
            try {
                log.info("CACHE STORE prefix={} in node {}", normalizedPrefix, targetNode.getNodeId());
                redisTemplate.opsForValue().set(cacheKey, suggestions, 5, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("CACHE FALLBACK: Error writing to Redis node {}: {}", targetNode.getNodeId(), e.getMessage());
            }
        }

        return suggestions;
    }
}

