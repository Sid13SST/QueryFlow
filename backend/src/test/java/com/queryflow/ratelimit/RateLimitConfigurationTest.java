package com.queryflow.ratelimit;

import com.queryflow.controller.SuggestionController;
import com.queryflow.service.SuggestionService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Limits are driven purely by configuration: no code change is needed to tune or disable them. */
class RateLimitConfigurationTest {

    @Nested
    @WebMvcTest(controllers = SuggestionController.class,
            properties = {"queryflow.rate-limit.endpoints.suggest.capacity=1",
                    "queryflow.rate-limit.endpoints.suggest.refill-per-second=0.01"})
    @Import(RateLimitConfig.class)
    class TightLimitFromProperties {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        SuggestionService suggestionService;

        @Test
        void secondRequestIsRejected() throws Exception {
            mvc.perform(get("/suggest").param("q", "a")).andExpect(status().isOk());
            mvc.perform(get("/suggest").param("q", "a")).andExpect(status().isTooManyRequests());
        }
    }

    @Nested
    @WebMvcTest(controllers = SuggestionController.class,
            properties = {"queryflow.rate-limit.enabled=false",
                    "queryflow.rate-limit.endpoints.suggest.capacity=1",
                    "queryflow.rate-limit.endpoints.suggest.refill-per-second=0.01"})
    @Import(RateLimitConfig.class)
    class DisabledByProperty {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        SuggestionService suggestionService;

        @Test
        void neverRejects() throws Exception {
            for (int i = 0; i < 50; i++) {
                mvc.perform(get("/suggest").param("q", "a")).andExpect(status().isOk());
            }
        }
    }

    @Nested
    @WebMvcTest(controllers = SuggestionController.class,
            properties = {"queryflow.rate-limit.trust-forwarded-header=true",
                    "queryflow.rate-limit.endpoints.suggest.capacity=1",
                    "queryflow.rate-limit.endpoints.suggest.refill-per-second=0.01"})
    @Import(RateLimitConfig.class)
    class BehindReverseProxy {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        SuggestionService suggestionService;

        @Test
        void usesRightMostForwardedAddressAsClient() throws Exception {
            // Same proxy remote address, different real clients -> separate buckets.
            mvc.perform(get("/suggest").param("q", "a").header("X-Forwarded-For", "203.0.113.1"))
                    .andExpect(status().isOk());
            mvc.perform(get("/suggest").param("q", "a").header("X-Forwarded-For", "203.0.113.2"))
                    .andExpect(status().isOk());
            // A spoofed left-most entry does not buy a fresh bucket.
            mvc.perform(get("/suggest").param("q", "a").header("X-Forwarded-For", "1.1.1.1, 203.0.113.1"))
                    .andExpect(status().isTooManyRequests());
        }
    }

    /**
     * Render: Cloudflare -> Render proxy -> app. Visitors share the Cloudflare edge and Render
     * proxy addresses, so keying on either would let one visitor lock out everyone behind it.
     */
    @Nested
    @WebMvcTest(controllers = {SuggestionController.class, com.queryflow.controller.RateLimitInfoController.class},
            properties = {"queryflow.rate-limit.trust-forwarded-header=true",
                    "queryflow.rate-limit.client-ip-header=CF-Connecting-IP",
                    "queryflow.rate-limit.endpoints.suggest.capacity=1",
                    "queryflow.rate-limit.endpoints.suggest.refill-per-second=0.01"})
    @Import(RateLimitConfig.class)
    class BehindRenderAndCloudflare {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        SuggestionService suggestionService;

        @Test
        void visitorsSharingAnEdgeGetSeparateBuckets() throws Exception {
            String sharedEdgeChain = "198.51.100.1, 172.70.1.1";
            mvc.perform(get("/suggest").param("q", "a").with(remote("10.25.239.2"))
                            .header("X-Forwarded-For", sharedEdgeChain).header("CF-Connecting-IP", "198.51.100.1"))
                    .andExpect(status().isOk());
            // A second visitor behind the same Cloudflare edge and Render proxy is not affected.
            mvc.perform(get("/suggest").param("q", "a").with(remote("10.25.239.2"))
                            .header("X-Forwarded-For", "198.51.100.2, 172.70.1.1").header("CF-Connecting-IP", "198.51.100.2"))
                    .andExpect(status().isOk());
            // The first visitor is limited, whatever it puts in X-Forwarded-For.
            mvc.perform(get("/suggest").param("q", "a").with(remote("10.25.239.2"))
                            .header("X-Forwarded-For", "1.2.3.4, 198.51.100.1, 172.70.1.1").header("CF-Connecting-IP", "198.51.100.1"))
                    .andExpect(status().isTooManyRequests());
        }

        @Test
        void clientEndpointReportsResolvedAddressAndLimits() throws Exception {
            mvc.perform(get("/ratelimit/client").header("CF-Connecting-IP", "198.51.100.9")
                            .header("X-Forwarded-For", "6.6.6.6, 198.51.100.9, 172.70.1.1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clientIp").value("198.51.100.9"))
                    .andExpect(jsonPath("$.source").value("CLIENT_IP_HEADER"))
                    .andExpect(jsonPath("$.limits.suggest.burst").value(1))
                    .andExpect(jsonPath("$.limits.search.burst").value(10));
        }

        private org.springframework.test.web.servlet.request.RequestPostProcessor remote(String addr) {
            return req -> {
                req.setRemoteAddr(addr);
                return req;
            };
        }
    }

    @Test
    void environmentVariablesOverrideDefaults() {
        new ApplicationContextRunner()
                .withUserConfiguration(RateLimitConfig.class)
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("test-systemEnvironment", Map.of(
                                "QUERYFLOW_RATELIMIT_ENDPOINTS_SUGGEST_CAPACITY", "77",
                                "QUERYFLOW_RATELIMIT_ENDPOINTS_SUGGEST_REFILLPERSECOND", "7.5",
                                "QUERYFLOW_RATELIMIT_ENDPOINTS_SUGGEST_PATHS", "/suggest"))))
                .run(ctx -> {
                    RateLimitProperties props = ctx.getBean(RateLimitProperties.class);
                    assertThat(props.getEndpoints().get("suggest").getCapacity()).isEqualTo(77);
                    assertThat(props.getEndpoints().get("suggest").getRefillPerSecond()).isEqualTo(7.5);
                    assertThat(props.getEndpoints().get("suggest").getPaths()).isEqualTo(List.of("/suggest"));
                    // Untouched rules keep their built-in defaults.
                    assertThat(props.getEndpoints().get("search").getCapacity()).isEqualTo(10);
                });
    }
}
