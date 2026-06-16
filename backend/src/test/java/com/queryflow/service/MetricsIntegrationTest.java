package com.queryflow.service;

import com.queryflow.dto.BenchmarkReportResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsIntegrationTest {

    private SearchQueryRepository searchQueryRepository;
    private ConsistentHashService consistentHashService;
    private CacheMetricsService cacheMetricsService;
    private CacheInvalidationService cacheInvalidationService;
    private PrefixGeneratorService prefixGeneratorService;
    private TrendingScoreService trendingScoreService;
    
    private MetricsService metricsService;
    private SearchBufferService searchBufferService;
    private SuggestionService suggestionService;
    private TrendingService trendingService;
    private BatchWriterService batchWriterService;
    private SearchService searchService;
    private BenchmarkService benchmarkService;

    @BeforeEach
    void setUp() {
        searchQueryRepository = mock(SearchQueryRepository.class);
        consistentHashService = mock(ConsistentHashService.class);
        
        cacheMetricsService = new CacheMetricsService();
        prefixGeneratorService = new PrefixGeneratorService();
        trendingScoreService = new TrendingScoreService();
        
        cacheInvalidationService = new CacheInvalidationService();
        ReflectionTestUtils.setField(cacheInvalidationService, "prefixGeneratorService", prefixGeneratorService);
        ReflectionTestUtils.setField(cacheInvalidationService, "consistentHashService", consistentHashService);
        
        metricsService = new MetricsService();
        ReflectionTestUtils.setField(metricsService, "cacheMetricsService", cacheMetricsService);
        ReflectionTestUtils.setField(metricsService, "cacheInvalidationService", cacheInvalidationService);

        searchBufferService = new SearchBufferService();

        suggestionService = new SuggestionService();
        ReflectionTestUtils.setField(suggestionService, "searchQueryRepository", searchQueryRepository);
        ReflectionTestUtils.setField(suggestionService, "consistentHashService", consistentHashService);
        ReflectionTestUtils.setField(suggestionService, "cacheMetricsService", cacheMetricsService);
        ReflectionTestUtils.setField(suggestionService, "metricsService", metricsService);

        trendingService = new TrendingService();
        ReflectionTestUtils.setField(trendingService, "searchQueryRepository", searchQueryRepository);
        ReflectionTestUtils.setField(trendingService, "consistentHashService", consistentHashService);
        ReflectionTestUtils.setField(trendingService, "cacheMetricsService", cacheMetricsService);
        ReflectionTestUtils.setField(trendingService, "trendingScoreService", trendingScoreService);
        ReflectionTestUtils.setField(trendingService, "metricsService", metricsService);
        ReflectionTestUtils.setField(trendingService, "candidateLimit", 500);
        ReflectionTestUtils.setField(trendingService, "limit", 10);
        ReflectionTestUtils.setField(trendingService, "ttlMinutes", 5);

        batchWriterService = new BatchWriterService();
        ReflectionTestUtils.setField(batchWriterService, "searchBufferService", searchBufferService);
        ReflectionTestUtils.setField(batchWriterService, "searchQueryRepository", searchQueryRepository);
        ReflectionTestUtils.setField(batchWriterService, "cacheInvalidationService", cacheInvalidationService);
        ReflectionTestUtils.setField(batchWriterService, "metricsService", metricsService);

        searchService = new SearchService();
        ReflectionTestUtils.setField(searchService, "searchBufferService", searchBufferService);
        ReflectionTestUtils.setField(searchService, "metricsService", metricsService);

        benchmarkService = new BenchmarkService();
        ReflectionTestUtils.setField(benchmarkService, "metricsService", metricsService);
        
        // Ensure consistentHashService routes to null (forces cache miss)
        when(consistentHashService.route(anyString())).thenReturn(null);
    }

    @Test
    void testSuggestionFlowIncrementsMetrics() {
        when(searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(anyString()))
                .thenReturn(Collections.emptyList());

        assertEquals(0, metricsService.getSuggestionRequests());
        assertEquals(0, metricsService.getDatabaseReads());

        suggestionService.getSuggestions("test");

        assertEquals(1, metricsService.getSuggestionRequests());
        assertEquals(1, metricsService.getDatabaseReads());
        assertTrue(metricsService.getSuggestionAvgMs() >= 0);
        assertTrue(metricsService.getSuggestionMaxMs() >= 0);
        assertEquals(1, metricsService.getCacheMisses());
    }

    @Test
    void testTrendingFlowIncrementsMetrics() {
        when(searchQueryRepository.findTopTrending(any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        assertEquals(0, metricsService.getTrendingRequests());
        assertEquals(0, metricsService.getDatabaseReads());

        trendingService.getTrending();

        assertEquals(1, metricsService.getTrendingRequests());
        assertEquals(1, metricsService.getDatabaseReads());
        assertTrue(metricsService.getTrendingAvgMs() >= 0);
        assertTrue(metricsService.getTrendingMaxMs() >= 0);
        assertEquals(1, metricsService.getCacheMisses());
    }

    @Test
    void testSearchSubmissionIncrementsSearches() {
        assertEquals(0, metricsService.getTotalSearches());

        searchService.recordSearch("hello");
        searchService.recordSearch("world");

        assertEquals(2, metricsService.getTotalSearches());
        assertEquals(2, searchBufferService.getPendingEntries());
    }

    @Test
    void testBatchFlushIncrementsMetrics() {
        searchService.recordSearch("apple");
        searchService.recordSearch("banana");

        when(searchQueryRepository.findByQueryIn(anyCollection()))
                .thenReturn(Collections.emptyList());

        assertEquals(0, metricsService.getBatchFlushCount());
        assertEquals(0, metricsService.getDatabaseReads());
        assertEquals(0, metricsService.getDatabaseWrites());

        batchWriterService.scheduleFlush();

        assertEquals(1, metricsService.getBatchFlushCount());
        assertEquals(1, metricsService.getDatabaseReads()); // inside persistBatch lookup
        assertEquals(1, metricsService.getDatabaseWrites()); // inside persistBatch saveAll
        assertTrue(metricsService.getLastFlushDurationMs() >= 0);
        assertTrue(metricsService.getAverageFlushDurationMs() >= 0);
    }

    @Test
    void testBenchmarkCalculation() {
        // Mock 10 searches and 2 flushes (which equal to 2 databaseWrites)
        for (int i = 0; i < 10; i++) {
            metricsService.incrementTotalSearches();
        }
        metricsService.incrementDatabaseWrites();
        metricsService.incrementDatabaseWrites();

        // Add 5 hits and 5 misses to calculate hit rate
        cacheMetricsService.incrementHits();
        cacheMetricsService.incrementHits();
        cacheMetricsService.incrementHits();
        cacheMetricsService.incrementMisses();
        cacheMetricsService.incrementMisses();

        BenchmarkReportResponse report = benchmarkService.generateReport();
        
        assertEquals(60.0, report.getCacheHitRate());
        assertEquals(3, report.getEstimatedDbReadsSaved());
        // Reduction = (10 - 2) / 10 * 100 = 80%
        assertEquals(80.0, report.getEstimatedWriteReduction());
    }
}
