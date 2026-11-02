package com.queryflow.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Per-client-IP, per-endpoint token bucket rate limiter.
 *
 * State is held in this JVM. With N backend instances behind a round-robin load
 * balancer, a single client can therefore get up to N x the configured rate in
 * the worst case. That trade-off is deliberate (see docs/ARCHITECTURE.md,
 * "What stays instance-local"): it keeps rate limiting working when Redis is
 * down, and the per-instance limit can be set to global/N.
 */
@Slf4j
public class RateLimiterService {

    private final RateLimitProperties properties;
    private final LongSupplier nanoClock;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> allowedByRule = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> rejectedByRule = new ConcurrentHashMap<>();
    private final AtomicLong untracked = new AtomicLong();

    public RateLimiterService(RateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    public RateLimiterService(RateLimitProperties properties, LongSupplier nanoClock) {
        this.properties = properties;
        this.nanoClock = nanoClock;
        properties.getEndpoints().forEach((name, rule) -> {
            if (rule.getCapacity() <= 0 || rule.getRefillPerSecond() <= 0) {
                throw new IllegalStateException("queryflow.rate-limit.endpoints." + name
                        + " must have capacity > 0 and refill-per-second > 0");
            }
            allowedByRule.put(name, new AtomicLong());
            rejectedByRule.put(name, new AtomicLong());
        });
        log.info("Rate limiting {} with rules {}", properties.isEnabled() ? "ENABLED" : "DISABLED",
                properties.getEndpoints());
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** Finds the rule whose path pattern matches, if any. */
    public Optional<String> matchRule(String path) {
        for (Map.Entry<String, RateLimitProperties.Rule> e : properties.getEndpoints().entrySet()) {
            for (String pattern : e.getValue().getPaths()) {
                if (pathMatcher.match(pattern, path)) {
                    return Optional.of(e.getKey());
                }
            }
        }
        return Optional.empty();
    }

    public RateLimitProperties.Rule getRule(String ruleName) {
        return properties.getEndpoints().get(ruleName);
    }

    public TokenBucket.Result tryAcquire(String ruleName, String clientIp) {
        RateLimitProperties.Rule rule = properties.getEndpoints().get(ruleName);
        long now = nanoClock.getAsLong();
        String key = ruleName + "|" + clientIp;

        TokenBucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= properties.getMaxTrackedBuckets()) {
                evictIdle();
            }
            if (buckets.size() >= properties.getMaxTrackedBuckets()) {
                untracked.incrementAndGet();
                allowedByRule.get(ruleName).incrementAndGet();
                return new TokenBucket.Result(true, rule.getCapacity(), 0L);
            }
            bucket = buckets.computeIfAbsent(key,
                    k -> new TokenBucket(rule.getCapacity(), rule.getRefillPerSecond(), now));
        }

        TokenBucket.Result result = bucket.tryConsume(now);
        (result.allowed() ? allowedByRule : rejectedByRule).get(ruleName).incrementAndGet();
        return result;
    }

    /** Drops buckets that have been idle for longer than the configured TTL. */
    public int evictIdle() {
        long now = nanoClock.getAsLong();
        long idleNanos = TimeUnit.SECONDS.toNanos(properties.getIdleBucketTtlSeconds());
        int before = buckets.size();
        buckets.entrySet().removeIf(e -> e.getValue().isIdleSince(now, idleNanos));
        return before - buckets.size();
    }

    public long getTotalAllowed() {
        return allowedByRule.values().stream().mapToLong(AtomicLong::get).sum();
    }

    public long getTotalRejected() {
        return rejectedByRule.values().stream().mapToLong(AtomicLong::get).sum();
    }

    public long getUntracked() {
        return untracked.get();
    }

    public int getTrackedBuckets() {
        return buckets.size();
    }

    /** Per-rule counters, e.g. {"suggest": {"allowed": 10, "rejected": 2}}. */
    public Map<String, Map<String, Long>> getCountersByRule() {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        for (String name : properties.getEndpoints().keySet()) {
            Map<String, Long> c = new LinkedHashMap<>();
            c.put("allowed", allowedByRule.get(name).get());
            c.put("rejected", rejectedByRule.get(name).get());
            out.put(name, c);
        }
        return out;
    }
}
