package com.queryflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.queryflow.dto.CacheNode;
import com.queryflow.dto.TrendingExplainResponse;
import com.queryflow.dto.TrendingResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TrendingService {

    @Value("${queryflow.trending.limit:10}")
    private int limit;

    @Value("${queryflow.trending.cache.ttl-minutes:5}")
    private int ttlMinutes;

    @Value("${queryflow.trending.candidate-limit:500}")
    private int candidateLimit;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private ConsistentHashService consistentHashService;

    @Autowired
    private CacheMetricsService cacheMetricsService;

    @Autowired
    private TrendingScoreService trendingScoreService;

    @Autowired
    private MetricsService metricsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public List<TrendingResponse> getTrending() {
        long startTime = System.currentTimeMillis();
        metricsService.incrementTrendingRequests();
        
        try {
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
            metricsService.incrementDatabaseReads();

            log.info("Trending score calculation started");
            // Fetch candidates from PostgreSQL
            List<SearchQuery> candidates = searchQueryRepository.findTopTrending(PageRequest.of(0, candidateLimit));
            log.info("Trending ranking generated with candidates size {}", candidates.size());

            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }

            // The first candidate in count DESC has the maximum count
            long maxCount = candidates.get(0).getCount();
            LocalDateTime now = LocalDateTime.now();

            List<TrendingResponse> sortedTrending = candidates.stream()
                    .map(q -> {
                        double popularity = trendingScoreService.calculatePopularityScore(q.getCount(), maxCount);
                        double recency = trendingScoreService.calculateRecencyScore(q.getLastSearched(), now);
                        double score = trendingScoreService.calculateFinalScore(popularity, recency);
                        // Round final score to 2 decimal places for clean representation
                        double roundedScore = Math.round(score * 100.0) / 100.0;
                        return TrendingResponse.builder()
                                .query(q.getQuery())
                                .count(q.getCount())
                                .score(roundedScore)
                                .build();
                    })
                    .sorted((r1, r2) -> {
                        int compare = Double.compare(r2.getScore(), r1.getScore());
                        if (compare == 0) {
                            return Long.compare(r2.getCount(), r1.getCount());
                        }
                        return compare;
                    })
                    .collect(Collectors.toList());

            // Slice to the output limit
            List<TrendingResponse> result = sortedTrending.stream()
                    .limit(limit)
                    .collect(Collectors.toList());

            if (targetNode != null) {
                RedisTemplate<String, Object> redisTemplate = targetNode.getRedisTemplate();
                try {
                    log.info("TRENDING CACHE STORE in node {}", targetNode.getNodeId());
                    redisTemplate.opsForValue().set(cacheKey, result, ttlMinutes, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("TRENDING CACHE FALLBACK: Error writing to Redis node {}: {}", targetNode.getNodeId(), e.getMessage());
                }
            }

            return result;
        } finally {
            metricsService.recordTrendingLatency(System.currentTimeMillis() - startTime);
        }
    }

    public Optional<TrendingExplainResponse> explainTrending(String queryStr) {
        if (queryStr == null || queryStr.trim().isEmpty()) {
            return Optional.empty();
        }
        
        metricsService.incrementDatabaseReads();
        Optional<SearchQuery> queryOpt = searchQueryRepository.findByQuery(queryStr.trim());
        if (queryOpt.isEmpty()) {
            return Optional.empty();
        }
        
        SearchQuery sq = queryOpt.get();
        
        // Find max count from database to normalize popularity score
        long maxCount = 0;
        metricsService.incrementDatabaseReads();
        List<SearchQuery> top = searchQueryRepository.findTopTrending(PageRequest.of(0, 1));
        if (!top.isEmpty()) {
            maxCount = top.get(0).getCount();
        }

        LocalDateTime now = LocalDateTime.now();
        double popularity = trendingScoreService.calculatePopularityScore(sq.getCount(), maxCount);
        double recency = trendingScoreService.calculateRecencyScore(sq.getLastSearched(), now);
        double finalScore = trendingScoreService.calculateFinalScore(popularity, recency);

        double hoursSinceLastSearch = 0.0;
        if (sq.getLastSearched() != null) {
            hoursSinceLastSearch = Duration.between(sq.getLastSearched(), now).toMillis() / 3600000.0;
        }

        // Round all double metrics to 2 decimal places for readable API response
        double roundedHours = Math.round(hoursSinceLastSearch * 100.0) / 100.0;
        double roundedPopularity = Math.round(popularity * 100.0) / 100.0;
        double roundedRecency = Math.round(recency * 100.0) / 100.0;
        double roundedFinal = Math.round(finalScore * 100.0) / 100.0;

        TrendingExplainResponse explanation = TrendingExplainResponse.builder()
                .query(sq.getQuery())
                .count(sq.getCount())
                .hoursSinceLastSearch(roundedHours)
                .popularityScore(roundedPopularity)
                .recencyScore(roundedRecency)
                .finalScore(roundedFinal)
                .build();

        return Optional.of(explanation);
    }
}
