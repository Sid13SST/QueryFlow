package com.queryflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryflow.dto.CacheNode;
import com.queryflow.dto.TrendingResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TrendingService {

    @Value("${queryflow.trending.limit:10}")
    private int limit;

    @Value("${queryflow.trending.cache.ttl-minutes:5}")
    private int ttlMinutes;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private ConsistentHashService consistentHashService;

    @Autowired
    private CacheMetricsService cacheMetricsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public List<TrendingResponse> getTrending() {
        log.info("TRENDING REQUEST");
        
        String cacheKey = "trending:top";
        CacheNode targetNode = consistentHashService.route(cacheKey);

        if (targetNode != null) {
            log.info("trendingKey={} routedTo={}", cacheKey, targetNode.getNodeId());
            RedisTemplate<String, Object> redisTemplate = targetNode.getRedisTemplate();
            try {
                Object cachedObj = redisTemplate.opsForValue().get(cacheKey);
                if (cachedObj instanceof List) {
                    log.info("TRENDING CACHE HIT");
                    cacheMetricsService.incrementHits();
                    
                    List<?> cachedList = (List<?>) cachedObj;
                    return cachedList.stream()
                            .map(item -> objectMapper.convertValue(item, TrendingResponse.class))
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("TRENDING CACHE FALLBACK: Error reading from Redis node {}: {}", targetNode.getNodeId(), e.getMessage());
            }
        } else {
            log.warn("TRENDING CACHE FALLBACK: No cache nodes available in consistent hash ring");
        }

        log.info("TRENDING CACHE MISS");
        cacheMetricsService.incrementMisses();

        // Fetch from Postgres
        List<SearchQuery> queries = searchQueryRepository.findTopTrending(PageRequest.of(0, limit));
        log.info("Number of trending results returned from database: {}", queries.size());

        List<TrendingResponse> trending = queries.stream()
                .map(q -> new TrendingResponse(q.getQuery(), q.getCount()))
                .collect(Collectors.toList());

        if (targetNode != null) {
            RedisTemplate<String, Object> redisTemplate = targetNode.getRedisTemplate();
            try {
                log.info("TRENDING CACHE STORE in node {}", targetNode.getNodeId());
                redisTemplate.opsForValue().set(cacheKey, trending, ttlMinutes, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("TRENDING CACHE FALLBACK: Error writing to Redis node {}: {}", targetNode.getNodeId(), e.getMessage());
            }
        }

        return trending;
    }
}
