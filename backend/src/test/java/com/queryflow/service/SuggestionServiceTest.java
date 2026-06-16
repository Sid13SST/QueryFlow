package com.queryflow.service;

import com.queryflow.dto.CacheNode;
import com.queryflow.dto.SuggestionResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuggestionServiceTest {

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

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private SuggestionService suggestionService;

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
    }

    @Test
    void testCacheHitPath() {
        // Arrange
        List<SuggestionResponse> cachedSuggestions = Arrays.asList(
                new SuggestionResponse("iphone 15", 100000L),
                new SuggestionResponse("iphone 14", 80000L)
        );
        
        when(consistentHashService.route("suggest:iphone")).thenReturn(mockNode);
        when(valueOperations.get("suggest:iphone")).thenReturn(cachedSuggestions);

        // Act
        List<SuggestionResponse> results = suggestionService.getSuggestions("iphone");

        // Assert
        assertEquals(2, results.size());
        assertEquals("iphone 15", results.get(0).getQuery());
        assertEquals("iphone 14", results.get(1).getQuery());

        verify(consistentHashService, times(1)).route("suggest:iphone");
        verify(searchQueryRepository, never()).findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(anyString());
        verify(cacheMetricsService, times(1)).incrementHits();
        verify(cacheMetricsService, never()).incrementMisses();
    }

    @Test
    void testCacheMissPath() {
        // Arrange
        when(consistentHashService.route("suggest:iphone")).thenReturn(mockNode);
        when(valueOperations.get("suggest:iphone")).thenReturn(null);

        SearchQuery q1 = SearchQuery.builder().query("iphone 15").count(100000L).build();
        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("iphone"))
                .thenReturn(Collections.singletonList(q1));

        // Act
        List<SuggestionResponse> results = suggestionService.getSuggestions("iphone");

        // Assert
        assertEquals(1, results.size());
        assertEquals("iphone 15", results.get(0).getQuery());
        
        verify(consistentHashService, times(1)).route("suggest:iphone");
        verify(searchQueryRepository, times(1)).findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("iphone");
        verify(cacheMetricsService, times(1)).incrementMisses();
        verify(cacheMetricsService, never()).incrementHits();
        verify(valueOperations, times(1)).set(eq("suggest:iphone"), anyList(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void testNodeFailureFallback() {
        // Arrange
        when(consistentHashService.route("suggest:iphone")).thenReturn(mockNode);
        // Simulate Redis connection failure
        when(valueOperations.get("suggest:iphone")).thenThrow(new RuntimeException("Redis connection lost"));

        SearchQuery q1 = SearchQuery.builder().query("iphone 15").count(100000L).build();
        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("iphone"))
                .thenReturn(Collections.singletonList(q1));

        // Act - should NOT crash and fallback to DB
        List<SuggestionResponse> results = suggestionService.getSuggestions("iphone");

        // Assert
        assertEquals(1, results.size());
        assertEquals("iphone 15", results.get(0).getQuery());
        
        verify(searchQueryRepository, times(1)).findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("iphone");
        verify(cacheMetricsService, times(1)).incrementMisses();
    }

    @Test
    void testEmptyPrefixThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> suggestionService.getSuggestions(""));
        assertThrows(IllegalArgumentException.class, () -> suggestionService.getSuggestions("   "));
        assertThrows(IllegalArgumentException.class, () -> suggestionService.getSuggestions(null));
    }

    @Test
    void testCaseInsensitiveSearch() {
        // Arrange
        when(consistentHashService.route("suggest:java")).thenReturn(mockNode);
        when(valueOperations.get("suggest:java")).thenReturn(null);

        SearchQuery q1 = SearchQuery.builder().query("Java").count(50000L).build();
        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("java"))
                .thenReturn(Collections.singletonList(q1));

        // Act
        List<SuggestionResponse> results = suggestionService.getSuggestions("jAvA");

        // Assert
        assertEquals(1, results.size());
        assertEquals("Java", results.get(0).getQuery());
        verify(searchQueryRepository, times(1)).findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("java");
    }
}


