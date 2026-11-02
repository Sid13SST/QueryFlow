package com.queryflow.service;

import com.queryflow.controller.BatchController;
import com.queryflow.dto.BatchStatusResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BatchWriteSystemTest {

    private SearchQueryRepository searchQueryRepository;
    private SearchBufferService searchBufferService;
    private BatchWriterService batchWriterService;
    private BatchController batchController;

    @BeforeEach
    void setUp() {
        searchQueryRepository = mock(SearchQueryRepository.class);
        searchBufferService = new SearchBufferService();
        CacheInvalidationService cacheInvalidationService = mock(CacheInvalidationService.class);
        MetricsService metricsService = mock(MetricsService.class);
        
        batchWriterService = new BatchWriterService();
        ReflectionTestUtils.setField(batchWriterService, "searchBufferService", searchBufferService);
        ReflectionTestUtils.setField(batchWriterService, "searchQueryRepository", searchQueryRepository);
        ReflectionTestUtils.setField(batchWriterService, "cacheInvalidationService", cacheInvalidationService);
        ReflectionTestUtils.setField(batchWriterService, "metricsService", metricsService);

        batchController = new BatchController();
        ReflectionTestUtils.setField(batchController, "searchBufferService", searchBufferService);
        ReflectionTestUtils.setField(batchController, "batchWriterService", batchWriterService);
    }

    @Test
    void testBufferIncrementAndEventCount() {
        searchBufferService.increment("spring boot");
        searchBufferService.increment("spring boot");
        searchBufferService.increment("java");

        assertEquals(2, searchBufferService.getPendingEntries());
        assertEquals(3L, searchBufferService.getPendingEvents());
    }

    @Test
    void testBufferFlushAndRollback() {
        searchBufferService.increment("spring boot");
        searchBufferService.increment("java");

        Map<String, Integer> snapshot = searchBufferService.flush();
        assertEquals(2, snapshot.size());
        assertEquals(1, snapshot.get("spring boot"));
        assertEquals(1, snapshot.get("java"));

        assertEquals(0, searchBufferService.getPendingEntries());
        assertEquals(0L, searchBufferService.getPendingEvents());

        // Rollback
        searchBufferService.rollback(snapshot);
        assertEquals(2, searchBufferService.getPendingEntries());
        assertEquals(2L, searchBufferService.getPendingEvents());
    }

    @Test
    void testScheduledFlushEmptyBuffer() {
        batchWriterService.scheduleFlush();
        verifyNoInteractions(searchQueryRepository);
        assertNotNull(batchWriterService.getLastFlushTime());
    }

    @Test
    void testScheduledFlushPersistsExistingAndNewEntities() {
        searchBufferService.increment("spring boot"); // new
        searchBufferService.increment("java");        // existing
        searchBufferService.increment("java");

        // "java" exists: the atomic increment touches 1 row. "spring boot" does not: 0 rows.
        when(searchQueryRepository.incrementCount(eq("java"), eq(2L), any())).thenReturn(1);
        when(searchQueryRepository.incrementCount(eq("spring boot"), eq(1L), any())).thenReturn(0);

        batchWriterService.scheduleFlush();

        // Existing rows are incremented in the database, never loaded and overwritten.
        verify(searchQueryRepository).incrementCount(eq("java"), eq(2L), any());
        verify(searchQueryRepository, never()).findByQueryIn(anyCollection());

        // Only the new query is inserted.
        ArgumentCaptor<List<SearchQuery>> captor = ArgumentCaptor.forClass(List.class);
        verify(searchQueryRepository).saveAllAndFlush(captor.capture());

        List<SearchQuery> savedList = captor.getValue();
        assertEquals(1, savedList.size());
        SearchQuery savedSpring = savedList.get(0);
        assertEquals("spring boot", savedSpring.getQuery());
        assertEquals(1L, savedSpring.getCount());
        assertNotNull(savedSpring.getLastSearched());

        // Verify buffer cleared
        assertEquals(0, searchBufferService.getPendingEntries());
        assertEquals(0L, searchBufferService.getPendingEvents());
    }

    @Test
    void testFlushRollsBackOnDatabaseFailure() {
        searchBufferService.increment("spring boot");
        searchBufferService.increment("java");

        // Force exception on the database write
        when(searchQueryRepository.incrementCount(anyString(), anyLong(), any()))
                .thenThrow(new RuntimeException("Database offline"));

        batchWriterService.scheduleFlush();

        // Verify rollback happened (buffer is not cleared/lost)
        assertEquals(2, searchBufferService.getPendingEntries());
        assertEquals(2L, searchBufferService.getPendingEvents());
    }

    @Test
    void testBatchStatusEndpoint() {
        searchBufferService.increment("spring boot");
        searchBufferService.increment("java");

        ResponseEntity<BatchStatusResponse> responseEntity = batchController.getBatchStatus();
        BatchStatusResponse body = responseEntity.getBody();

        assertNotNull(body);
        assertEquals(2, body.getPendingEntries());
        assertEquals(2L, body.getPendingEvents());
        assertNull(body.getLastFlushTime()); // Not flushed yet
    }
}
