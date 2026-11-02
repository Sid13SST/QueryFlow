package com.queryflow.service;

import com.queryflow.cache.ResilientCacheClient;
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
    private ResilientCacheClient cacheClient;

    private final AtomicLong invalidationCount = new AtomicLong(0);
    private final AtomicLong deferredInvalidationCount = new AtomicLong(0);
    private volatile LocalDateTime lastInvalidationTime;

    public void invalidateQueries(Collection<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return;
        }

        for (String query : queries) {
            for (String prefix : prefixGeneratorService.generatePrefixes(query)) {
                invalidateKey("suggest:" + prefix);
            }
        }
    }

    public void invalidateTrending() {
        invalidateKey("trending:top");
    }

    /**
     * Deletes one key on its owning node. If the node is unreachable the client
     * queues the DEL and replays it before the node serves reads again, so the
     * invalidation is deferred rather than lost.
     */
    private void invalidateKey(String key) {
        if (cacheClient.delete(key)) {
            invalidationCount.incrementAndGet();
            lastInvalidationTime = LocalDateTime.now();
            log.info("CACHE INVALIDATED key={}", key);
        } else {
            deferredInvalidationCount.incrementAndGet();
            log.warn("CACHE INVALIDATION DEFERRED key={} (owning node unavailable)", key);
        }
    }

    public long getInvalidationCount() {
        return invalidationCount.get();
    }

    public long getDeferredInvalidationCount() {
        return deferredInvalidationCount.get();
    }

    public LocalDateTime getLastInvalidationTime() {
        return lastInvalidationTime;
    }
}
