# QueryFlow Architecture

QueryFlow is a high-performance, distributed autocomplete search suggestion and trending searches platform. The system is designed to minimize database read/write bottlenecks by combining distributed caching, consistent hashing, in-memory batch write aggregation, and active targeted cache invalidation.

---

## 1. System Overview

QueryFlow coordinates several key systems to manage high-throughput queries:
* **PostgreSQL**: The relational persistence layer holding canonical search metadata and long-term query logs.
* **Consistent Hash Ring**: A ring mapping keys to three Redis instances (Node 1, Node 2, Node 3) using MD5 hashing with 100 virtual nodes per physical node for balanced key distribution.
* **In-Memory Write Buffer**: Aggregates incoming searches in RAM to reduce database transactional write load.
* **Batch Flusher**: Periodically persists buffered searches to PostgreSQL using bulk updates, then triggers active cache invalidations.

---

## 2. Component Diagram

```text
       Search Submissions               Autocomplete / Suggestions
               │                                     │
               ▼                                     ▼
         [Search API]                        [Suggestion API]
               │                                     │
               ▼                                     ▼
      [In-Memory Buffer]                    [Consistent Hash Ring]
               │                                 /   │   \
         (30s Delay)                            /    │    \
               ▼                               ▼     ▼     ▼
       [Batch Flusher]                    [Node 1] [Node 2] [Node 3] (Redis)
         /          \                         \      │      /
        /            \                         \     │     / (Cache Misses)
       ▼              ▼                         ▼    ▼    ▼
[PostgreSQL] ──► [Cache Invalidator] ────────► [Targeted Key Eviction]
```

---

## 3. Cache Architecture & Consistent Hashing

Cache keys are distributed across Redis nodes via consistent hashing to prevent cache hotspots.

### Hash Ring Mechanics
1. **Hashing Function**: An MD5 digest is computed for keys. The first 8 bytes of the digest are read as a 64-bit integer (`long`) mapping to positions on a $2^{64}$ coordinate ring.
2. **Replication/Virtual Nodes**: Each of the 3 physical Redis nodes registers 100 virtual node tokens on the ring. This maintains uniform distribution and prevents skewing (hot spotting) on a single physical node.
3. **Key Routing**: A cache key's MD5 hash is matched against the ring. The system traverses clockwise to the nearest virtual node token and sends the request to that token's corresponding physical Redis node.
4. **Resilience**: If a Redis node fails, the ring continues routing requests to the remaining online nodes. Read misses fall back gracefully to PostgreSQL, keeping the service fully operational.

---

## 4. Batch Write Pipeline

Direct updates to PostgreSQL on every search cause heavy lock contention and transaction overhead under load.

### Aggregation Mechanism
1. **In-Memory Buffer**: Incoming searches are pushed to `ConcurrentHashMap` counters inside `SearchBufferService`. No database transaction is initialized.
2. **Scheduled Flush**: A scheduled task (running every 30 seconds) flushes the buffer snapshot.
3. **Bulk Merge**: The batch task queries the database in a single round-trip (`findByQueryIn`), resolves existing records, increments counts, sets the last searched time, and commits them all in a single bulk write transaction (`saveAll`).
4. **Failure Recovery**: If the database transaction fails (e.g. database offline), the snapshot is rolled back into the in-memory buffer to prevent data loss, and retried on the next cycle.

---

## 5. Advanced Trending Ranking

Instead of standard ranking by count descending (which causes old queries to stagnate at the top), QueryFlow implements a configurable popularity-recency decay algorithm.

### Scoring Formula
$$\text{Score} = 0.7 \times \text{PopularityScore} + 0.3 \times \text{RecencyScore}$$

* **PopularityScore**: Normalized against the maximum search count in the top candidate pool (up to 500 candidate queries).
* **RecencyScore**: Employs inverse harmonic decay based on hours elapsed since the query was last searched:
  $$\text{RecencyScore} = \frac{1.0}{\text{hoursSinceLastSearch} + 1.0}$$

This score is computed in memory on the top candidates, sorted descending, and cached under the `trending:top` Redis key.

---

## 6. Cache Invalidation Strategy

Using TTL alone results in stale suggestions or outdated trending lists for up to 5 minutes after a database flush.

### Invalidation Mechanics
1. **Prefix Generation**: For every query updated in a database batch flush, `PrefixGeneratorService` generates all prefixes (e.g., `"iphone"` -> `["i", "ip", "iph", "ipho", "iphon", "iphone"]`).
2. **Targeted Eviction**: The system routes each prefix's suggestion key (`suggest:{prefix}`) to its target Redis node on the hash ring and deletes only that key.
3. **Resilience**: Outages in Redis nodes log warnings but never block or rollback the PostgreSQL database transactional updates.
