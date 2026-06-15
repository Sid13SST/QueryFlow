package com.queryflow.service;

import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchServiceTest {

    @Mock
    private SearchQueryRepository searchQueryRepository;

    @InjectMocks
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testExistingQueryIncremented() {
        SearchQuery existing = SearchQuery.builder()
                .id(1L)
                .query("iphone")
                .count(100L)
                .build();

        when(searchQueryRepository.findByQuery("iphone")).thenReturn(Optional.of(existing));

        searchService.recordSearch("iphone");

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchQueryRepository).save(captor.capture());
        
        SearchQuery saved = captor.getValue();
        assertEquals("iphone", saved.getQuery());
        assertEquals(101L, saved.getCount());
        assertNotNull(saved.getLastSearched());
    }

    @Test
    void testNewQueryInserted() {
        when(searchQueryRepository.findByQuery("iphone 18")).thenReturn(Optional.empty());

        searchService.recordSearch("iphone 18");

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchQueryRepository).save(captor.capture());

        SearchQuery saved = captor.getValue();
        assertEquals("iphone 18", saved.getQuery());
        assertEquals(1L, saved.getCount());
        assertNotNull(saved.getLastSearched());
    }

    @Test
    void testEmptyQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> searchService.recordSearch(""));
        assertThrows(IllegalArgumentException.class, () -> searchService.recordSearch(null));
    }

    @Test
    void testWhitespaceQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> searchService.recordSearch("    "));
    }

    @Test
    void testQueryNormalizationTrimsWhitespace() {
        SearchQuery existing = SearchQuery.builder()
                .id(1L)
                .query("java")
                .count(50L)
                .build();

        when(searchQueryRepository.findByQuery("java")).thenReturn(Optional.of(existing));

        searchService.recordSearch("   java   ");

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(searchQueryRepository).save(captor.capture());

        SearchQuery saved = captor.getValue();
        assertEquals("java", saved.getQuery());
        assertEquals(51L, saved.getCount());
        assertNotNull(saved.getLastSearched());
    }
}
