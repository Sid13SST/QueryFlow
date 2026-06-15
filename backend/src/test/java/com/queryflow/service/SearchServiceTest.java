package com.queryflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchServiceTest {

    @Mock
    private SearchBufferService searchBufferService;

    @InjectMocks
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testExistingQueryIncremented() {
        searchService.recordSearch("iphone");
        verify(searchBufferService).increment("iphone");
    }

    @Test
    void testNewQueryInserted() {
        searchService.recordSearch("iphone 18");
        verify(searchBufferService).increment("iphone 18");
    }

    @Test
    void testEmptyQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> searchService.recordSearch(""));
        assertThrows(IllegalArgumentException.class, () -> searchService.recordSearch(null));
        verifyNoInteractions(searchBufferService);
    }

    @Test
    void testWhitespaceQueryRejected() {
        assertThrows(IllegalArgumentException.class, () -> searchService.recordSearch("    "));
        verifyNoInteractions(searchBufferService);
    }

    @Test
    void testQueryNormalizationTrimsWhitespace() {
        searchService.recordSearch("   java   ");
        verify(searchBufferService).increment("java");
    }
}
