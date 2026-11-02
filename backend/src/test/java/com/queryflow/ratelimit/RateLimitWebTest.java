package com.queryflow.ratelimit;

import com.queryflow.cache.ResilientCacheClient;
import com.queryflow.controller.MetricsController;
import com.queryflow.controller.SearchController;
import com.queryflow.controller.SuggestionController;
import com.queryflow.controller.TrendingController;
import com.queryflow.dto.SuggestionResponse;
import com.queryflow.service.CacheInvalidationService;
import com.queryflow.service.MetricsService;
import com.queryflow.service.SearchService;
import com.queryflow.service.SuggestionService;
import com.queryflow.service.TrendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end through the servlet filter chain and real controllers, with the
 * services mocked. Limits are shrunk via properties so the test is fast.
 */
@WebMvcTest(controllers = {SuggestionController.class, SearchController.class,
        TrendingController.class, MetricsController.class},
        properties = {
                "queryflow.rate-limit.endpoints.suggest.capacity=3",
                "queryflow.rate-limit.endpoints.suggest.refill-per-second=0.1",
                "queryflow.rate-limit.endpoints.search.capacity=2",
                "queryflow.rate-limit.endpoints.search.refill-per-second=0.1",
                "queryflow.rate-limit.endpoints.trending.capacity=2",
                "queryflow.rate-limit.endpoints.trending.refill-per-second=0.1",
                "queryflow.instance-id=test-instance"
        })
@Import(RateLimitConfig.class)
class RateLimitWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SuggestionService suggestionService;
    @MockitoBean
    private SearchService searchService;
    @MockitoBean
    private TrendingService trendingService;
    @MockitoBean
    private MetricsService metricsService;
    @MockitoBean
    private ResilientCacheClient cacheClient;
    @MockitoBean
    private CacheInvalidationService cacheInvalidationService;

    @BeforeEach
    void stubServices() {
        when(suggestionService.getSuggestions(anyString()))
                .thenReturn(List.of(new SuggestionResponse("iphone 15", 80000L)));
        when(trendingService.getTrending()).thenReturn(List.of());
    }

    private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder b, String ip) {
        return b.with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
    }

    @Test
    void underLimitTrafficPassesWithRateLimitHeaders() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(get("/suggest").param("q", "iph"), "198.51.100.1"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "3"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(2 - i)))
                    .andExpect(jsonPath("$[0].query").value("iphone 15"));
        }
    }

    @Test
    void overLimitTrafficGets429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(get("/suggest").param("q", "iph"), "198.51.100.2")).andExpect(status().isOk());
        }
        mvc.perform(from(get("/suggest").param("q", "iph"), "198.51.100.2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "10")) // 1 token / 0.1 per second
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(jsonPath("$.endpoint").value("suggest"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(10));

        // A different client is unaffected.
        mvc.perform(from(get("/suggest").param("q", "iph"), "198.51.100.3")).andExpect(status().isOk());
    }

    @Test
    void searchAndTrendingAreLimitedIndependently() throws Exception {
        String ip = "198.51.100.4";
        for (int i = 0; i < 2; i++) {
            mvc.perform(from(post("/search").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"query\":\"java\"}"), ip)).andExpect(status().isOk());
        }
        mvc.perform(from(post("/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"java\"}"), ip)).andExpect(status().isTooManyRequests());

        mvc.perform(from(get("/trending"), ip)).andExpect(status().isOk());
        mvc.perform(from(get("/trending/explain").param("query", "java"), ip)).andExpect(status().isNotFound());
        mvc.perform(from(get("/trending"), ip)).andExpect(status().isTooManyRequests());
    }

    @Test
    void metricsEndpointIsNotLimitedAndExposesCounters() throws Exception {
        String ip = "198.51.100.5";
        for (int i = 0; i < 5; i++) {
            mvc.perform(from(get("/suggest").param("q", "a"), ip));
        }
        // 3 allowed + 2 rejected from this client; other tests share the context so assert >=.
        for (int i = 0; i < 10; i++) {
            mvc.perform(from(get("/metrics"), ip)).andExpect(status().isOk());
        }
        mvc.perform(from(get("/metrics"), ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateLimitRejected").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.rateLimitAllowed").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.rateLimitByEndpoint.suggest.rejected")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.rateLimitTrackedBuckets").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.instanceId").value("test-instance"))
                .andExpect(header().string("X-QueryFlow-Instance", "test-instance"));
    }

    @Test
    void metricsCountersMoveExactlyWithTraffic() throws Exception {
        long allowedBefore = rateLimiter.getTotalAllowed();
        long rejectedBefore = rateLimiter.getTotalRejected();
        String ip = "198.51.100.6";
        for (int i = 0; i < 4; i++) {
            mvc.perform(from(get("/suggest").param("q", "x"), ip));
        }
        org.junit.jupiter.api.Assertions.assertEquals(allowedBefore + 3, rateLimiter.getTotalAllowed());
        org.junit.jupiter.api.Assertions.assertEquals(rejectedBefore + 1, rateLimiter.getTotalRejected());
    }

    @Autowired
    private RateLimiterService rateLimiter;
}
