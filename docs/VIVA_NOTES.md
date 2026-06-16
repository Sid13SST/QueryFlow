# QueryFlow — Viva / Interview Preparation Notes

This document prepares you for project evaluation discussions.
Each section covers a subsystem with anticipated questions, concise answers, and design justifications.

---

## 1. Database Choice — PostgreSQL

**Q: Why PostgreSQL over MongoDB or an in-memory store?**

A: PostgreSQL provides ACID guarantees that ensure our search query counts never drift due to concurrent updates. The `ON CONFLICT` / `merge` pattern (upsert) is natively supported and is critical for the batch write pipeline — we can atomically insert-or-update thousands of rows in a single transaction. MongoDB could work, but PostgreSQL's B-Tree and functional indexes (`LOWER(query)`) give us efficient prefix-based autocomplete queries, which are the core read path.

**Q: How do you handle prefix search efficiently?**

A: We created a functional index on `LOWER(query)` so case-insensitive prefix queries (`LOWER(query) LIKE LOWER('prefix%')`) use an index scan instead of a full table scan. Spring Data JPA derives the query from the method name `findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc`, which maps to this SQL pattern.

**Q: What are the scaling limits?**

A: PostgreSQL comfortably handles millions of rows with proper indexing. With our batch write system, write load is reduced by ~97% (1000 searches → ~1 bulk write), so write throughput is not a bottleneck. For read-heavy workloads (autocomplete), the distributed Redis cache absorbs the majority of reads, keeping PostgreSQL under low load.

---

## 2. Redis Cache Architecture

**Q: Why use Redis instead of an in-app cache like Caffeine?**

A: Redis allows cache sharing across multiple backend instances. If we horizontally scaled the backend to 3 JVMs behind a load balancer, an in-process cache (Caffeine) would have 3 separate caches with no coordination. Redis gives us a single shared cache that all backend instances read from, ensuring consistent suggestions across requests.

**Q: How are cache keys structured?**

A: Two key patterns:
- `suggest:{prefix}` — Stores the top-10 autocomplete results for a given prefix (e.g., `suggest:iph`).
- `trending:top` — Stores the pre-computed top-N trending results.

Both use a TTL of 5 minutes as a safety net, but active invalidation evicts keys much sooner after database updates.

---

## 3. Consistent Hashing & Virtual Nodes

**Q: What is consistent hashing and why did you use it?**

A: Consistent hashing maps cache keys to a ring of positions. Each Redis node occupies positions on this ring. When a key needs to be cached or retrieved, we hash the key, find its position on the ring, and route to the nearest node clockwise.

The advantage over simple modular hashing (`key.hashCode() % 3`) is resilience: if one Redis node goes down, only keys assigned to that node are redistributed. With modular hashing, adding or removing a node redistributes nearly all keys.

**Q: What are virtual nodes and why use 100 per physical node?**

A: A single hash position per physical node creates uneven key distribution — one node might get 60% of keys. Virtual nodes solve this by giving each physical node 100 distinct positions on the ring. This statistically smooths the distribution so each node handles approximately 33% of keys. The number 100 is a balance between distribution uniformity and lookup overhead.

**Q: What hashing function do you use and why?**

A: We use MD5 to compute hash positions. MD5's output has excellent uniformity across the keyspace, which prevents hotspotting. We take the first 8 bytes of the MD5 digest as a `long` value, giving us positions across the full `Long.MIN_VALUE` to `Long.MAX_VALUE` range. We don't need cryptographic strength — just uniform distribution.

**Q: How does the system handle a Redis node failure?**

A: The consistent hash ring continues routing to remaining nodes. If a read to the target node fails, the service catches the exception, logs a warning, and falls back to PostgreSQL. The suggestion/trending response is still served — just with higher latency for that request. On the next request the result is cached on the available nodes.

---

## 4. Batch Write System

**Q: What problem does batch writing solve?**

A: Without batching, 1000 searches generate 1000 individual `UPDATE` statements to PostgreSQL. Each opens a transaction, acquires a row lock, commits, and releases. Under high load this causes lock contention, connection pool exhaustion, and increased latency.

With batching, those 1000 searches are aggregated in a `ConcurrentHashMap` in memory. Every 30 seconds, the buffer is flushed as a single database transaction that:
1. Reads all affected rows in one query (`findByQueryIn`)
2. Increments counts in memory
3. Writes all updates in one `saveAll()` call

This reduces 1000 writes to ~1 write.

**Q: How does `ConcurrentHashMap` handle thread safety?**

A: `ConcurrentHashMap.merge()` is used with `Long::sum` as the remapping function. This is an atomic operation that handles concurrent increments without explicit locking. When flushing, we snapshot the map entries and clear them atomically.

**Q: What happens if the database flush fails?**

A: The flushed entries are rolled back into the in-memory buffer using `merge()` so no data is lost. The next scheduled flush (30 seconds later) will retry the combined set.

**Q: Why 30 seconds?**

A: It's a configurable trade-off. Shorter intervals (e.g., 5 seconds) reduce data staleness but increase database write frequency. Longer intervals (e.g., 2 minutes) reduce writes further but increase the risk of data loss if the JVM crashes. 30 seconds is a reasonable default that keeps counts fresh enough for trending while batching efficiently.

---

## 5. Advanced Trending — Popularity + Recency Ranking

**Q: Why not just sort by count descending?**

A: Count-only ranking creates a stagnant leaderboard. A query searched 100,000 times in 2024 (e.g., "iphone") will permanently dominate, even if nobody is searching for it now. Meanwhile, a viral query searched 500 times in the last hour (e.g., "chatgpt agents") would never surface.

**Q: Explain the scoring formula.**

A:
```
finalScore = 0.7 × popularityScore + 0.3 × recencyScore
```

- **Popularity Score** = `count / maxCount` (normalized 0–1)
- **Recency Score** = `1.0 / (hoursSinceLastSearch + 1.0)` (inverse decay, range 0–1)

A query searched 1 minute ago gets `recencyScore ≈ 0.98`. A query last searched 24 hours ago gets `recencyScore ≈ 0.04`. This naturally decays old queries.

**Q: Why 0.7/0.3 weights?**

A: 70% popularity / 30% recency ensures established popular queries aren't immediately displaced by a single recent search, but fresh activity has enough weight to push emerging trends into the top 10. The weights are externalized in `application.properties` and can be tuned without code changes.

**Q: Why use a candidate pool of 500?**

A: Computing scores for the entire table would be expensive. Instead, we fetch the top 500 by count (a fast indexed query), compute scores in memory, re-sort by final score, and return the top 10. This limits database load while still giving recency a chance to promote queries that are outside the strict top-10-by-count.

---

## 6. Cache Invalidation Strategy

**Q: Why not rely on TTL alone?**

A: With a 5-minute TTL, a user who searches "iphone" would continue seeing stale suggestions for up to 5 minutes after new data is flushed to PostgreSQL. Active invalidation ensures that after each batch flush, the affected cache keys are immediately evicted, so the next request gets fresh data.

**Q: How does prefix-based invalidation work?**

A: When the query "iphone" is updated in the database, we generate all prefixes: `["i", "ip", "iph", "ipho", "iphon", "iphone"]`. For each prefix, we construct the cache key `suggest:{prefix}`, route it through the consistent hash ring to find the correct Redis node, and delete it. This ensures that typing "ip" or "iphone" will both trigger fresh lookups.

**Q: Doesn't this cause a cache stampede?**

A: In practice, no. Only the specific prefix keys affected by the batch flush are evicted — not the entire cache. After a flush of 50 unique queries, perhaps 200 prefix keys are evicted across 3 Redis nodes. The remaining thousands of cached suggestions are unaffected. Even the evicted keys are quickly repopulated on the next user request.

**Q: How is `trending:top` invalidated?**

A: After every batch flush, the `trending:top` key is routed through the hash ring and deleted. The next `GET /trending` request recomputes the scores from PostgreSQL and re-caches the result.

---

## 7. Metrics & Observability (Phase 10)

**Q: What metrics does the system track?**

A:
| Metric | Description |
|---|---|
| `suggestionRequests` | Total autocomplete API calls |
| `trendingRequests` | Total trending API calls |
| `totalSearches` | Total searches submitted |
| `databaseReads` | DB read operations |
| `databaseWrites` | DB write operations (batch flushes) |
| `cacheHits / cacheMisses` | Redis cache hit/miss counts |
| `cacheHitRate` | Percentage of requests served from cache |
| `batchFlushCount` | Number of batch flush cycles executed |
| `avgFlushDurationMs` | Average time per flush |
| `suggestionAvgMs / maxMs` | Suggestion endpoint latency |
| `trendingAvgMs / maxMs` | Trending endpoint latency |
| `cacheInvalidations` | Total keys invalidated |

**Q: How are metrics stored?**

A: All metrics use `java.util.concurrent.atomic.AtomicLong` counters in `MetricsService`. This is lock-free and thread-safe without synchronization overhead. Metrics are ephemeral (reset on restart) since they're for observability, not persistence.

**Q: What does the benchmark report show?**

A: `GET /benchmark/report` returns:
- **Cache Hit Rate**: Percentage of reads served from Redis (higher = fewer DB reads)
- **Estimated DB Reads Saved**: Absolute count of reads absorbed by cache
- **Write Reduction**: Percentage reduction in DB writes from batch aggregation
- **Batch Flushes**: Number of flush cycles completed

---

## 8. System Architecture — End-to-End Flow

### Search Flow
```
User types "iphone" → POST /search
  → SearchService.search("iphone")
    → metricsService.incrementTotalSearches()
    → searchBufferService.add("iphone", 1)  // In-memory buffer, no DB call
    → Return immediate response
```

### Batch Flush (every 30 seconds)
```
Scheduled task triggers → BatchWriterService.flush()
  → Snapshot buffer → Clear buffer
  → findByQueryIn(keys)  // Single DB read
  → Merge counts, set lastSearched
  → saveAll(entities)     // Single DB write
  → cacheInvalidationService.invalidateForQueries(keys)
    → Generate prefixes → Route via hash ring → Delete from Redis
    → Delete trending:top
```

### Autocomplete Flow
```
User types "ip" → GET /suggest?q=ip
  → SuggestionService.suggest("ip")
    → Hash "suggest:ip" → Route to Redis Node 2
    → Cache HIT? → Return cached results
    → Cache MISS? → Query PostgreSQL → Cache result → Return
```

### Trending Flow
```
User opens trending → GET /trending
  → TrendingService.getTrending()
    → Hash "trending:top" → Route to Redis Node 1
    → Cache HIT? → Return cached list
    → Cache MISS? → Fetch top 500 from DB → Score & rank → Cache → Return
```

---

## 9. Trade-offs & Limitations

| Decision | Trade-off |
|---|---|
| In-memory buffer | Risk of losing up to 30 seconds of search data on JVM crash |
| 3 Redis nodes | Limited by single-machine resources (not a true distributed cluster) |
| MD5 hashing | Not cryptographically secure, but unnecessary for cache routing |
| Candidate pool of 500 | A query ranked #501 by count can never trend, even if very recent |
| Active invalidation | Adds latency to the flush cycle (~1-5ms per prefix) |
| AtomicLong metrics | Lost on restart (acceptable for development/demo; use Prometheus for production) |

---

## 10. If You Had More Time — Future Improvements

1. **Horizontal Scaling**: Deploy multiple backend instances behind a load balancer. Redis cache is already shared; the in-memory buffer would need to be replaced with a message queue (Kafka/RabbitMQ).
2. **Persistent Metrics**: Export to Prometheus + Grafana for time-series dashboards.
3. **Trie-based Suggestions**: Replace SQL prefix queries with an in-memory Trie for O(k) lookups.
4. **Rate Limiting**: Protect the search endpoint from abuse with token bucket rate limiting.
5. **A/B Testing Weights**: Serve different popularity/recency weights to different users and measure engagement.
