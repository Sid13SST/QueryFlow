package com.queryflow.service;

import com.queryflow.dto.CacheNode;
import com.queryflow.dto.TrendingResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @InjectMocks
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
                
        // Set properties using ReflectionTestUtils
        ReflectionTestUtils.setField(trendingService, "limit", 10);
        ReflectionTestUtils.setField(trendingService, "ttlMinutes", 5);
    }

    @Test
    void testCacheHitPath() {
        // Arrange
        List<TrendingResponse> cachedTrending = Arrays.asList(
                new TrendingResponse("iphone", 100000L),
                new TrendingResponse("java", 90000L)
        );
        
        when(consistentHashService.route("trending:top")).thenReturn(mockNode);
        when(valueOperations.get("trending:top")).thenReturn(cachedTrending);

        // Act
        List<TrendingResponse> results = trendingService.getTrending();

        // Assert
        assertEquals(2, results.size());
        assertEquals("iphone", results.get(0).getQuery());
        assertEquals(100000L, results.get(0).getCount());
        assertEquals("java", results.get(1).getQuery());
        assertEquals(90000L, results.get(1).getCount());

        verify(consistentHashService, times(1)).route("trending:top");
        verify(searchQueryRepository, never()).findTopTrending(any());
        verify(cacheMetricsService, times(1)).incrementHits();
        verify(cacheMetricsService, never()).incrementMisses();
    }

    @Test
    void testCacheMissPath() {
        // Arrange
        when(consistentHashService.route("trending:top")).thenReturn(mockNode);
        when(valueOperations.get("trending:top")).thenReturn(null);

        SearchQuery q1 = SearchQuery.builder().query("iphone").count(100000L).build();
        SearchQuery q2 = SearchQuery.builder().query("java").count(90000L).build();
        when(searchQueryRepository.findTopTrending(PageRequest.of(0, 10)))
                .thenReturn(Arrays.asList(q1, q2));

        // Act
        List<TrendingResponse> results = trendingService.getTrending();

        // Assert
        assertEquals(2, results.size());
        assertEquals("iphone", results.get(0).getQuery());
        assertEquals("java", results.get(1).getQuery());
        
        verify(consistentHashService, times(1)).route("trending:top");
        verify(searchQueryRepository, times(1)).findTopTrending(PageRequest.of(0, 10));
        verify(cacheMetricsService, times(1)).incrementMisses();
        verify(cacheMetricsService, never()).incrementHits();
        verify(valueOperations, times(1)).set(eq("trending:top"), anyList(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void testCorrectOrdering() {
        // Arrange
        when(consistentHashService.route("trending:top")).thenReturn(mockNode);
        when(valueOperations.get("trending:top")).thenReturn(null);

        // Repo returns in order of count DESC (simulating PostgreSQL query)
        SearchQuery q1 = SearchQuery.builder().query("iphone").count(100000L).build();
        SearchQuery q2 = SearchQuery.builder().query("java").count(90000L).build();
        SearchQuery q3 = SearchQuery.builder().query("spring boot").count(70000L).build();
        
        when(searchQueryRepository.findTopTrending(PageRequest.of(0, 10)))
                .thenReturn(Arrays.asList(q1, q2, q3));

        // Act
        List<TrendingResponse> results = trendingService.getTrending();

        // Assert
        assertEquals(3, results.size());
        assertEquals("iphone", results.get(0).getQuery());
        assertEquals("java", results.get(1).getQuery());
        assertEquals("spring boot", results.get(2).getQuery());
        assertTrue(results.get(0).getCount() >= results.get(1).getCount());
        assertTrue(results.get(1).getCount() >= results.get(2).getCount());
    }

    @Test
    void testConfigurableLimit() {
        // Arrange
        ReflectionTestUtils.setField(trendingService, "limit", 3);
        
        when(consistentHashService.route("trending:top")).thenReturn(mockNode);
        when(valueOperations.get("trending:top")).thenReturn(null);

        SearchQuery q1 = SearchQuery.builder().query("iphone").count(100000L).build();
        SearchQuery q2 = SearchQuery.builder().query("java").count(90000L).build();
        SearchQuery q3 = SearchQuery.builder().query("spring").count(80000L).build();
        
        when(searchQueryRepository.findTopTrending(PageRequest.of(0, 3)))
                .thenReturn(Arrays.asList(q1, q2, q3));

        // Act
        List<TrendingResponse> results = trendingService.getTrending();

        // Assert
        assertEquals(3, results.size());
        verify(searchQueryRepository, times(1)).findTopTrending(PageRequest.of(0, 3));
    }

    @Test
    void testEmptyDataset() {
        // Arrange
        when(consistentHashService.route("trending:top")).thenReturn(mockNode);
        when(valueOperations.get("trending:top")).thenReturn(null);
        when(searchQueryRepository.findTopTrending(any())).thenReturn(Collections.emptyList());

        // Act
        List<TrendingResponse> results = trendingService.getTrending();

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(searchQueryRepository, times(1)).findTopTrending(PageRequest.of(0, 10));
    }
}
