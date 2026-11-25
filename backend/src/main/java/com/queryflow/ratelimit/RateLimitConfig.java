package com.queryflow.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public RateLimiterService rateLimiterService(RateLimitProperties properties) {
        return new RateLimiterService(properties);
    }

    @Bean
    public ClientIpResolver clientIpResolver(RateLimitProperties properties) {
        return new ClientIpResolver(properties);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiterService rateLimiterService,
                                                                   ClientIpResolver clientIpResolver) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(rateLimiterService, clientIpResolver));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("rateLimitFilter");
        return registration;
    }

    @Bean
    public IdleBucketSweeper idleBucketSweeper(RateLimiterService rateLimiterService) {
        return new IdleBucketSweeper(rateLimiterService);
    }

    /** Periodically frees memory held by buckets of clients that went away. */
    public static class IdleBucketSweeper {
        private final RateLimiterService rateLimiterService;

        IdleBucketSweeper(RateLimiterService rateLimiterService) {
            this.rateLimiterService = rateLimiterService;
        }

        @Scheduled(fixedDelay = 60_000)
        public void sweep() {
            rateLimiterService.evictIdle();
        }
    }
}
