package com.queryflow.service;

import com.queryflow.controller.CacheInvalidationController;
import com.queryflow.dto.CacheInvalidationStatsResponse;
import com.queryflow.dto.CacheNode;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheInvalidationServiceTest {

    private PrefixGeneratorService prefixGeneratorService;
    private CacheInvalidationService cacheInvalidationService;
    private CacheInvalidationController cacheInvalidationController;

    @Mock
    private ConsistentHashService consistentHashService;

    @Mock
    private RedisTemplate<String, Object> redisTemplateNode1;

    @Mock
    private RedisTemplate<String, Object> redisTemplateNode2;

    private CacheNode node1;
    private CacheNode node2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        prefixGeneratorService = new PrefixGeneratorService();
        cacheInvalidationService = new CacheInvalidationService();
        cacheInvalidationController = new CacheInvalidationController();

        node1 = CacheNode.builder()
                .nodeId("redis-node-1")
                .redisTemplate(redisTemplateNode1)
                .build();

        node2 = CacheNode.builder()
                .nodeId("redis-node-2")
                .redisTemplate(redisTemplateNode2)
                .build();

        ReflectionTestUtils.setField(cacheInvalidationService, "prefixGeneratorService", prefixGeneratorService);
        ReflectionTestUtils.setField(cacheInvalidationService, "consistentHashService", consistentHashService);
        ReflectionTestUtils.setField(cacheInvalidationController, "cacheInvalidationService", cacheInvalidationService);
    }

    @Test
    void testPrefixGeneration() {
        List<String> prefixes = prefixGeneratorService.generatePrefixes("iphone");
        assertEquals(6, prefixes.size());
        assertEquals("i", prefixes.get(0));
        assertEquals("ip", prefixes.get(1));
        assertEquals("iph", prefixes.get(2));
        assertEquals("ipho", prefixes.get(3));
        assertEquals("iphon", prefixes.get(4));
        assertEquals("iphone", prefixes.get(5));

        assertTrue(prefixGeneratorService.generatePrefixes("").isEmpty());
        assertTrue(prefixGeneratorService.generatePrefixes(null).isEmpty());
        assertTrue(prefixGeneratorService.generatePrefixes("   ").isEmpty());
    }

    @Test
    void testPrefixInvalidationAndNodeRouting() {
        // "i" and "iph" route to node1
        // "ip" routes to node2
        when(consistentHashService.route("suggest:i")).thenReturn(node1);
        when(consistentHashService.route("suggest:ip")).thenReturn(node2);
        when(consistentHashService.route("suggest:iph")).thenReturn(node1);

        cacheInvalidationService.invalidateQueries(Collections.singletonList("iph"));

        // Verify key suggestions deleted on routed templates (targeted invalidation)
        verify(redisTemplateNode1, times(1)).delete("suggest:i");
        verify(redisTemplateNode2, times(1)).delete("suggest:ip");
        verify(redisTemplateNode1, times(1)).delete("suggest:iph");

        // Verify stats
        assertEquals(3, cacheInvalidationService.getInvalidationCount());
        assertNotNull(cacheInvalidationService.getLastInvalidationTime());
    }

    @Test
    void testTrendingInvalidationRouting() {
        when(consistentHashService.route("trending:top")).thenReturn(node2);

        cacheInvalidationService.invalidateTrending();

        // Verify deleted only on node 2 (targeted delete)
        verify(redisTemplateNode2, times(1)).delete("trending:top");
        verifyNoInteractions(redisTemplateNode1);

        assertEquals(1, cacheInvalidationService.getInvalidationCount());
    }

    @Test
    void testCacheFailureHandlingDoesNotThrow() {
        when(consistentHashService.route(anyString())).thenReturn(node1);
        // Throw exception on delete to simulate cache outage
        when(redisTemplateNode1.delete(anyString())).thenThrow(new RuntimeException("Redis connection lost"));

        // Invalidation should catch it, log warning, and complete without throwing
        assertDoesNotThrow(() -> cacheInvalidationService.invalidateQueries(Collections.singletonList("i")));
        assertDoesNotThrow(() -> cacheInvalidationService.invalidateTrending());
    }

    @Test
    void testFlushTriggeredInvalidation() {
        // Wire a BatchWriterService and verify it triggers cache invalidations
        SearchBufferService bufferService = new SearchBufferService();
        SearchQueryRepository repository = mock(SearchQueryRepository.class);
        CacheInvalidationService mockInvalidationService = mock(CacheInvalidationService.class);

        BatchWriterService writerService = new BatchWriterService();
        ReflectionTestUtils.setField(writerService, "searchBufferService", bufferService);
        ReflectionTestUtils.setField(writerService, "searchQueryRepository", repository);
        ReflectionTestUtils.setField(writerService, "cacheInvalidationService", mockInvalidationService);

        // Put search in buffer
        bufferService.increment("iphone");

        // Mock repository lookup & save
        when(repository.findByQueryIn(anyCollection())).thenReturn(Collections.emptyList());

        // Run flush
        writerService.scheduleFlush();

        // Verify both queries and trending invalidations were triggered
        verify(mockInvalidationService, times(1)).invalidateQueries(anySet());
        verify(mockInvalidationService, times(1)).invalidateTrending();
    }

    @Test
    void testStatsControllerEndpoint() {
        when(consistentHashService.route(anyString())).thenReturn(node1);
        cacheInvalidationService.invalidateTrending();

        ResponseEntity<CacheInvalidationStatsResponse> entity = cacheInvalidationController.getInvalidationStats();
        CacheInvalidationStatsResponse body = entity.getBody();

        assertNotNull(body);
        assertEquals(1, body.getInvalidations());
        assertNotNull(body.getLastInvalidationTime());
    }
}
