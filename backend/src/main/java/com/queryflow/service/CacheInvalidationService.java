package com.queryflow.service;

import com.queryflow.dto.CacheNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CacheInvalidationService manages targeted cache invalidations.
 *
 * Why TTL alone is insufficient:
 * While Time-To-Live (TTL) acts as a reliable safety net to prevent indefinitely stale data,
 * a 5-minute TTL means users can see inconsistent suggestions or outdated trending lists for up
 * to 5 minutes after a database batch flush has completed. This leads to poor user experience.
 *
 * Why invalidation is required:
 * Active cache invalidation ensures immediate data freshness. When the batch flusher writes updated
 * query counts to PostgreSQL, the cache must be updated immediately so subsequent search suggestion
 * requests immediately reflect the newly updated popularity scores and counts.
 *
 * Why targeted invalidation is preferred over full cache clears:
 * Clearing entire Redis caches or executing FLUSHDB/FLUSHALL broadcasts deletes to all keys, which
 * destroys unrelated cache entries and triggers a massive wave of cache misses (thundering herd problem).
 * Targeted invalidation calculates exact keys affected by the write batch and deletes only those keys,
 * maintaining high cache hit ratios.
 */
@Service
@Slf4j
public class CacheInvalidationService {

    @Autowired
    private PrefixGeneratorService prefixGeneratorService;

    @Autowired
    private ConsistentHashService consistentHashService;

    private final AtomicLong invalidationCount = new AtomicLong(0);
    private volatile LocalDateTime lastInvalidationTime;

    public void invalidateQueries(Collection<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return;
        }

        for (String query : queries) {
            for (String prefix : prefixGeneratorService.generatePrefixes(query)) {
                String key = "suggest:" + prefix;
                CacheNode node = consistentHashService.route(key);
                if (node != null) {
                    try {
                        node.getRedisTemplate().delete(key);
                        invalidationCount.incrementAndGet();
                        lastInvalidationTime = LocalDateTime.now();
                        log.info("CACHE INVALIDATED prefix={} node={}", prefix, node.getNodeId());
                    } catch (Exception e) {
                        log.warn("Failed to delete key '{}' on node '{}' during query invalidation: {}", 
                                key, node.getNodeId(), e.getMessage());
                    }
                }
            }
        }
    }

    public void invalidateTrending() {
        String key = "trending:top";
        CacheNode node = consistentHashService.route(key);
        if (node != null) {
            try {
                node.getRedisTemplate().delete(key);
                invalidationCount.incrementAndGet();
                lastInvalidationTime = LocalDateTime.now();
                log.info("TRENDING CACHE INVALIDATED");
            } catch (Exception e) {
                log.warn("Failed to delete key '{}' on node '{}' during trending invalidation: {}", 
                        key, node.getNodeId(), e.getMessage());
            }
        }
    }

    public long getInvalidationCount() {
        return invalidationCount.get();
    }

    public LocalDateTime getLastInvalidationTime() {
        return lastInvalidationTime;
    }
}
