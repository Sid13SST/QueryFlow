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
        
        batchWriterService = new BatchWriterService();
        ReflectionTestUtils.setField(batchWriterService, "searchBufferService", searchBufferService);
        ReflectionTestUtils.setField(batchWriterService, "searchQueryRepository", searchQueryRepository);

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

        SearchQuery javaQuery = SearchQuery.builder()
                .id(1L)
                .query("java")
                .count(5L)
                .build();

        // Mock findByQueryIn to return the existing java entity
        when(searchQueryRepository.findByQueryIn(anyCollection()))
                .thenReturn(Collections.singletonList(javaQuery));

        batchWriterService.scheduleFlush();

        // Verify saveAll was called
        ArgumentCaptor<List<SearchQuery>> captor = ArgumentCaptor.forClass(List.class);
        verify(searchQueryRepository).saveAll(captor.capture());

        List<SearchQuery> savedList = captor.getValue();
        assertEquals(2, savedList.size());

        SearchQuery savedJava = savedList.stream().filter(q -> q.getQuery().equals("java")).findFirst().orElseThrow();
        assertEquals(7L, savedJava.getCount()); // 5 + 2
        assertNotNull(savedJava.getLastSearched());

        SearchQuery savedSpring = savedList.stream().filter(q -> q.getQuery().equals("spring boot")).findFirst().orElseThrow();
        assertEquals(1L, savedSpring.getCount()); // new
        assertNotNull(savedSpring.getLastSearched());

        // Verify buffer cleared
        assertEquals(0, searchBufferService.getPendingEntries());
        assertEquals(0L, searchBufferService.getPendingEvents());
    }

    @Test
    void testFlushRollsBackOnDatabaseFailure() {
        searchBufferService.increment("spring boot");
        searchBufferService.increment("java");

        // Force exception on lookup/save
        when(searchQueryRepository.findByQueryIn(anyCollection()))
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
