package com.queryflow.service;

import com.queryflow.dto.CacheNode;
import com.queryflow.dto.TrendingExplainResponse;
import com.queryflow.dto.TrendingResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrendingServiceTest {

    @Mock
    private SearchQueryRepository searchQueryRepository;

    @Mock
    private ConsistentHashService consistentHashService;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private CacheMetricsService cacheMetricsService;

    private TrendingScoreService trendingScoreService;

    private TrendingService trendingService;

    private CacheNode mockNode;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        mockNode = CacheNode.builder()
                .nodeId("redis-node-1")
                .host("localhost")
                .port(6379)
                .redisTemplate(redisTemplate)
                .build();

        trendingScoreService = new TrendingScoreService();
        ReflectionTestUtils.setField(trendingScoreService, "popularityWeight", 0.7);
        ReflectionTestUtils.setField(trendingScoreService, "recencyWeight", 0.3);

        trendingService = new TrendingService();
        ReflectionTestUtils.setField(trendingService, "limit", 10);
        ReflectionTestUtils.setField(trendingService, "ttlMinutes", 5);
        ReflectionTestUtils.setField(trendingService, "candidateLimit", 500);
        ReflectionTestUtils.setField(trendingService, "searchQueryRepository", searchQueryRepository);
        ReflectionTestUtils.setField(trendingService, "consistentHashService", consistentHashService);
        ReflectionTestUtils.setField(trendingService, "cacheMetricsService", cacheMetricsService);
        ReflectionTestUtils.setField(trendingService, "trendingScoreService", trendingScoreService);
    }

    @Test
    void testPopularityScoreCalculation() {
        assertEquals(1.0, trendingScoreService.calculatePopularityScore(100, 100));
        assertEquals(0.5, trendingScoreService.calculatePopularityScore(50, 100));
        assertEquals(0.0, trendingScoreService.calculatePopularityScore(0, 100));
        assertEquals(0.0, trendingScoreService.calculatePopularityScore(50, 0));
    }

    @Test
    void testRecencyScoreCalculation() {
        LocalDateTime now = LocalDateTime.now();
        
        // Exactly now (0 hours) -> score 1.0
        assertEquals(1.0, trendingScoreService.calculateRecencyScore(now, now));
        
        // 1 hour ago -> 1 / (1 + 1) = 0.5
        assertEquals(0.5, trendingScoreService.calculateRecencyScore(now.minusHours(1), now));
        
        // Null date -> 0.0
        assertEquals(0.0, trendingScoreService.calculateRecencyScore(null, now));
        
        // Clock drift (future date) -> 1.0
        assertEquals(1.0, trendingScoreService.calculateRecencyScore(now.plusMinutes(5), now));
    }

    @Test
    void testFinalScoreCalculation() {
        // 0.7 * 1.0 + 0.3 * 0.5 = 0.7 + 0.15 = 0.85
        assertEquals(0.85, trendingScoreService.calculateFinalScore(1.0, 0.5));
    }

    @Test
    void testWeightConfiguration() {
        TrendingScoreService customService = new TrendingScoreService();
        ReflectionTestUtils.setField(customService, "popularityWeight", 0.5);
        ReflectionTestUtils.setField(customService, "recencyWeight", 0.5);

        // 0.5 * 1.0 + 0.5 * 0.5 = 0.75
        assertEquals(0.75, customService.calculateFinalScore(1.0, 0.5));
    }

    @Test
    void testTrendingOrderingAndCandidateFiltering() {
        when(consistentHashService.route("trending:top")).thenReturn(mockNode);
        when(valueOperations.get("trending:top")).thenReturn(null);

        LocalDateTime now = LocalDateTime.now();

        // q1: huge popularity but searched 2 days ago (48 hours)
        // popularity = 1.0
        // recency = 1 / (48 + 1) = 0.02
        // score = 0.7 * 1.0 + 0.3 * 0.02 = 0.7 + 0.006 = 0.71 (rounded)
        SearchQuery q1 = SearchQuery.builder()
                .query("iphone")
                .count(100000L)
                .lastSearched(now.minusHours(48))
                .build();

        // q2: low popularity but searched just now (0 hours)
        // popularity = 1000 / 100000 = 0.01
        // recency = 1.0
        // score = 0.7 * 0.01 + 0.3 * 1.0 = 0.007 + 0.3 = 0.31 (rounded)
        SearchQuery q2 = SearchQuery.builder()
                .query("agents")
                .count(1000L)
                .lastSearched(now)
                .build();

        // q3: medium popularity and searched 1 hour ago
        // popularity = 50000 / 100000 = 0.5
        // recency = 0.5
        // score = 0.7 * 0.5 + 0.3 * 0.5 = 0.35 + 0.15 = 0.5
        SearchQuery q3 = SearchQuery.builder()
                .query("java")
                .count(50000L)
                .lastSearched(now.minusHours(1))
                .build();

        when(searchQueryRepository.findTopTrending(PageRequest.of(0, 500)))
                .thenReturn(Arrays.asList(q1, q3, q2)); // sorted by count DESC

        List<TrendingResponse> results = trendingService.getTrending();

        assertEquals(3, results.size());
        // Ordered by score DESC: q1 (0.71) -> q3 (0.5) -> q2 (0.31)
        assertEquals("iphone", results.get(0).getQuery());
        assertEquals(0.71, results.get(0).getScore());

        assertEquals("java", results.get(1).getQuery());
        assertEquals(0.5, results.get(1).getScore());

        assertEquals("agents", results.get(2).getQuery());
        assertEquals(0.31, results.get(2).getScore());
    }

    @Test
    void testExplainEndpoint() {
        LocalDateTime now = LocalDateTime.now();
        SearchQuery targetQuery = SearchQuery.builder()
                .query("chatgpt agents")
                .count(5000L)
                .lastSearched(now.minusHours(2))
                .build();

        SearchQuery maxQuery = SearchQuery.builder()
                .query("iphone")
                .count(10000L)
                .build();

        when(searchQueryRepository.findByQuery("chatgpt agents")).thenReturn(Optional.of(targetQuery));
        when(searchQueryRepository.findTopTrending(PageRequest.of(0, 1)))
                .thenReturn(Collections.singletonList(maxQuery));

        Optional<TrendingExplainResponse> explanationOpt = trendingService.explainTrending("chatgpt agents");
        
        assertTrue(explanationOpt.isPresent());
        TrendingExplainResponse explanation = explanationOpt.get();

        assertEquals("chatgpt agents", explanation.getQuery());
        assertEquals(5000L, explanation.getCount());
        assertEquals(2.0, explanation.getHoursSinceLastSearch()); // rounded to 2.0
        assertEquals(0.5, explanation.getPopularityScore());       // 5000/10000 = 0.5
        assertEquals(0.33, explanation.getRecencyScore());       // 1/(2+1) = 0.33
        assertEquals(0.45, explanation.getFinalScore());         // 0.7*0.5 + 0.3*0.33 = 0.35 + 0.099 = 0.449 -> 0.45
    }

    @Test
    void testEmptyDataset() {
        when(consistentHashService.route("trending:top")).thenReturn(mockNode);
        when(valueOperations.get("trending:top")).thenReturn(null);
        when(searchQueryRepository.findTopTrending(any())).thenReturn(Collections.emptyList());

        List<TrendingResponse> results = trendingService.getTrending();
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
