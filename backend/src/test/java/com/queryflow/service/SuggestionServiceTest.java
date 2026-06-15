package com.queryflow.service;

import com.queryflow.dto.SuggestionResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuggestionServiceTest {

    @Mock
    private SearchQueryRepository searchQueryRepository;

    @InjectMocks
    private SuggestionService suggestionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testValidPrefixReturnsMatches() {
        SearchQuery q1 = SearchQuery.builder().query("iphone 15").count(100000L).build();
        SearchQuery q2 = SearchQuery.builder().query("iphone").count(80000L).build();
        
        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("iphone"))
                .thenReturn(Arrays.asList(q1, q2));

        List<SuggestionResponse> results = suggestionService.getSuggestions("iphone");

        assertEquals(2, results.size());
        assertEquals("iphone 15", results.get(0).getQuery());
        assertEquals(100000L, results.get(0).getCount());
        assertEquals("iphone", results.get(1).getQuery());
        assertEquals(80000L, results.get(1).getCount());
    }

    @Test
    void testNoMatchesReturnsEmptyList() {
        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("xyz"))
                .thenReturn(Collections.emptyList());

        List<SuggestionResponse> results = suggestionService.getSuggestions("xyz");

        assertTrue(results.isEmpty());
    }

    @Test
    void testEmptyPrefixThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> suggestionService.getSuggestions(""));
        assertThrows(IllegalArgumentException.class, () -> suggestionService.getSuggestions("   "));
        assertThrows(IllegalArgumentException.class, () -> suggestionService.getSuggestions(null));
    }

    @Test
    void testCaseInsensitiveSearch() {
        SearchQuery q1 = SearchQuery.builder().query("Java").count(50000L).build();
        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("jAvA"))
                .thenReturn(Collections.singletonList(q1));

        List<SuggestionResponse> results = suggestionService.getSuggestions("jAvA");

        assertEquals(1, results.size());
        assertEquals("Java", results.get(0).getQuery());
    }

    @Test
    void testLimitToTenResults() {
        List<SearchQuery> mockList = Arrays.asList(
                new SearchQuery(1L, "java 1", 100L, null),
                new SearchQuery(2L, "java 2", 90L, null),
                new SearchQuery(3L, "java 3", 80L, null),
                new SearchQuery(4L, "java 4", 70L, null),
                new SearchQuery(5L, "java 5", 60L, null),
                new SearchQuery(6L, "java 6", 50L, null),
                new SearchQuery(7L, "java 7", 40L, null),
                new SearchQuery(8L, "java 8", 30L, null),
                new SearchQuery(9L, "java 9", 20L, null),
                new SearchQuery(10L, "java 10", 10L, null)
        );

        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc("java"))
                .thenReturn(mockList);

        List<SuggestionResponse> results = suggestionService.getSuggestions("java");

        assertEquals(10, results.size());
        assertEquals("java 1", results.get(0).getQuery());
        assertEquals("java 10", results.get(9).getQuery());
    }
}
