package com.queryflow.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rate limiting settings, bound from {@code queryflow.rate-limit.*}.
 *
 * Every value can be changed in application.properties or through environment
 * variables (Spring relaxed binding, e.g.
 * {@code QUERYFLOW_RATELIMIT_ENDPOINTS_SUGGEST_CAPACITY=60}) without touching code.
 *
 * The defaults below are used when a property is absent. They are sized for the
 * bundled frontend, which debounces typeahead calls by 300 ms (at most ~3.3
 * /suggest calls per second per user), with headroom for several users sharing
 * one NAT address.
 */
@Data
@ConfigurationProperties(prefix = "queryflow.rate-limit")
public class RateLimitProperties {

    /** Master switch. When false, no request is ever limited or counted. */
    private boolean enabled = true;

    /**
     * When true, the client IP is taken from the right-most entry of
     * {@link #forwardedHeader} (the address the trusted reverse proxy saw).
     * Only enable this when the backend is reachable exclusively through that proxy,
     * otherwise clients can spoof the header.
     */
    private boolean trustForwardedHeader = false;

    /** Header written by the reverse proxy. nginx's $proxy_add_x_forwarded_for appends to X-Forwarded-For. */
    private String forwardedHeader = "X-Forwarded-For";

    /** Buckets untouched for this long are dropped (an idle bucket is full anyway, so nothing is lost). */
    private long idleBucketTtlSeconds = 300;

    /**
     * Upper bound on tracked client buckets, protecting memory. When reached after
     * evicting idle buckets, new clients are let through untracked (fail-open) and
     * counted in the {@code untracked} metric.
     */
    private int maxTrackedBuckets = 100_000;

    /** Rules keyed by a short name that appears in metrics. */
    private Map<String, Rule> endpoints = defaultRules();

    @Data
    public static class Rule {
        /** Ant-style path patterns this rule applies to, e.g. /trending/**. */
        private List<String> paths = new ArrayList<>();
        /** Maximum burst size (bucket capacity). */
        private long capacity = 20;
        /** Sustained requests per second (refill rate). */
        private double refillPerSecond = 10.0;

        public static Rule of(long capacity, double refillPerSecond, String... paths) {
            Rule r = new Rule();
            r.setCapacity(capacity);
            r.setRefillPerSecond(refillPerSecond);
            r.setPaths(new ArrayList<>(List.of(paths)));
            return r;
        }
    }

    private static Map<String, Rule> defaultRules() {
        Map<String, Rule> rules = new LinkedHashMap<>();
        rules.put("suggest", Rule.of(30, 10.0, "/suggest"));
        rules.put("search", Rule.of(10, 2.0, "/search"));
        rules.put("trending", Rule.of(20, 5.0, "/trending", "/trending/**"));
        return rules;
    }
}
