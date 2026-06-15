package com.queryflow.service;

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
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private CacheMetricsService cacheMetricsService;

    @InjectMocks
    private SuggestionService suggestionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testCacheHitPath() {
        // Prepare cached data
        List<SuggestionResponse> cachedSuggestions = Arrays.asList(
                new SuggestionResponse("iphone 15", 100000L),
                new SuggestionResponse("iphone 14", 80000L)
        );
        
        when(valueOperations.get("suggest:iphone")).thenReturn(cachedSuggestions);

        // Call method
        List<SuggestionResponse> results = suggestionService.getSuggestions("iphone");

        // Verify
        assertEquals(2, results.size());
        assertEquals("iphone 15", results.get(0).getQuery());
        assertEquals("iphone 14", results.get(1).getQuery());

        // Verify database is NOT queried
        verify(searchQueryRepository, never()).findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(anyString());
        
        // Verify metric hit is incremented
        verify(cacheMetricsService, times(1)).incrementHits();
        verify(cacheMetricsService, never()).incrementMisses();
    }

    @Test
    void testCacheMissPath() {
        // Cache misses
        when(valueOperations.get("suggest:iphone")).thenReturn(null);

        // Prepare database data
        SearchQuery q1 = SearchQuery.builder().query("iphone 15").count(100000L).build();
        SearchQuery q2 = SearchQuery.builder().query("iphone 14").count(80000L).build();
        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("iphone"))
                .thenReturn(Arrays.asList(q1, q2));

        // Call method
        List<SuggestionResponse> results = suggestionService.getSuggestions("iphone");

        // Verify results
        assertEquals(2, results.size());
        assertEquals("iphone 15", results.get(0).getQuery());
        
        // Verify database IS queried
        verify(searchQueryRepository, times(1)).findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("iphone");
        
        // Verify metric miss is incremented and data is stored
        verify(cacheMetricsService, times(1)).incrementMisses();
        verify(cacheMetricsService, never()).incrementHits();
        verify(valueOperations, times(1)).set(eq("suggest:iphone"), anyList(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void testEmptyPrefixThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> suggestionService.getSuggestions(""));
        assertThrows(IllegalArgumentException.class, () -> suggestionService.getSuggestions("   "));
        assertThrows(IllegalArgumentException.class, () -> suggestionService.getSuggestions(null));
    }

    @Test
    void testCaseInsensitiveSearch() {
        // Mock cache miss
        when(valueOperations.get("suggest:java")).thenReturn(null);

        SearchQuery q1 = SearchQuery.builder().query("Java").count(50000L).build();
        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("java"))
                .thenReturn(Collections.singletonList(q1));

        List<SuggestionResponse> results = suggestionService.getSuggestions("jAvA");

        assertEquals(1, results.size());
        assertEquals("Java", results.get(0).getQuery());
        verify(searchQueryRepository, times(1)).findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("java");
    }
}

