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
4. **Resilience**: If a Redis node fails, keys owned by that node are served from PostgreSQL while the other nodes keep serving theirs. Keys are deliberately *not* re-routed to the next node on the ring (see section 8).

---

## 4. Batch Write Pipeline

Direct updates to PostgreSQL on every search cause heavy lock contention and transaction overhead under load.

### Aggregation Mechanism
1. **In-Memory Buffer**: Incoming searches are pushed to `ConcurrentHashMap` counters inside `SearchBufferService`. No database transaction is initialized.
2. **Scheduled Flush**: A scheduled task (running every 30 seconds) flushes the buffer snapshot.
3. **Atomic Increments**: Inside one transaction, each buffered query is applied with `UPDATE search_queries SET count = count + :delta, last_searched = :now WHERE query = :q` (keys in sorted order). Queries that update 0 rows are new and are inserted with `saveAllAndFlush`. The count is never read into Java and written back, so concurrent flushes from several backend instances cannot overwrite each other (section 9).
4. **Failure Recovery**: If the transaction fails (database offline, or another instance inserted the same new query first), it rolls back as a whole and the snapshot is merged back into the in-memory buffer, then retried on the next cycle. Because nothing was committed, the retry cannot double count.

> Before the Reliability Lab branch the flush loaded rows, added the delta in Java and saved the absolute value, and the method's `@Transactional` was bypassed (it was called on `this`). Both are fixed; `MultiInstanceBatchWriteTest` fails against the old logic.

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
3. **Resilience**: Outages in Redis nodes never block or roll back the PostgreSQL update. A DEL that cannot reach its node is queued and replayed before that node serves another read (section 8), so an invalidation is deferred, not lost.

---

## 7. Rate Limiting

`RateLimitFilter` (a servlet filter, `com.queryflow.ratelimit`) applies a token bucket per **(endpoint group, client IP)**.

| Group | Paths | Burst (capacity) | Sustained (tokens/s) |
|---|---|---|---|
| `suggest` | `/suggest` | 30 | 10 |
| `search` | `/search` | 10 | 2 |
| `trending` | `/trending`, `/trending/**` | 20 | 5 |

* Why these defaults: the bundled frontend debounces typeahead by 300 ms, so one person produces at most ~3.3 `/suggest` calls/s. The limits leave room for several people behind one NAT address while stopping scripted floods.
* Over the limit: **HTTP 429**, `Retry-After: <seconds>` (time until the next token, rounded up), JSON body `{"message","endpoint","retryAfterSeconds"}`. Allowed responses carry `X-RateLimit-Limit` and `X-RateLimit-Remaining`.
* Everything is configuration (`queryflow.rate-limit.*`, or env vars such as `RATE_LIMIT_SUGGEST_CAPACITY`); new groups can be added in properties alone. `RATE_LIMIT_ENABLED=false` turns it off.
* Behind a proxy set `RATE_LIMIT_TRUST_FORWARDED=true`. The client is then the **right-most** `X-Forwarded-For` entry (the address the proxy itself saw); entries further left are client-supplied and ignored.
* Memory is bounded: idle buckets are swept every minute and the table is capped (`max-tracked-buckets`); past the cap new clients are let through untracked (fail-open) and counted.
* `/metrics` exposes `rateLimitAllowed`, `rateLimitRejected`, `rateLimitTrackedBuckets` and a per-group breakdown.

---

## 8. Redis Outage Resilience

All cache access goes through `ResilientCacheClient` (`com.queryflow.cache`). Callers get one of `HIT`, `MISS` or `UNAVAILABLE`; on `UNAVAILABLE` they read PostgreSQL and skip the cache write. The client never throws.

```text
 request ──► route(key) on hash ring ──► owner node N
                                            │
                       circuit[N] CLOSED ───┼──► Redis GET ──ok──► HIT / MISS
                                            │        └─error─► failure++ ──► UNAVAILABLE
                       circuit[N] OPEN ─────┴──► UNAVAILABLE immediately (no network call)
                                                           │
                                                           ▼
                                                PostgreSQL (correct, slower)
```

**Circuit breaker per node.** `failure-threshold` (3) consecutive errors open the node's circuit for `open-duration-ms` (5000). While open, calls for that node return immediately, so a dead node costs no timeouts. Afterwards exactly one probe (PING) is allowed; success closes the circuit, failure re-opens it. A background prober (`probe-interval-ms`, 2000) probes due circuits even without traffic, so recovery does not wait for a request. Lettuce's reconnect backoff is capped at 2 s (its default grows to 30 s), and Redis command/connect timeouts are 500 ms (configurable).

**Ring semantics are preserved.** A key whose owner is unhealthy is not re-routed to the next node. Losing one of three nodes loses about a third of the keys, and the other two thirds keep hitting. Re-routing was rejected because the fallback node would hold copies of keys it does not own. Targeted invalidation only deletes on the owner, so after recovery those copies could be served stale.

**No stale reads after a partition heals.** If a DEL (invalidation after a batch flush) cannot reach the owner, the key is queued for that node. Before the node serves its next call, the queue is replayed. If more than `max-pending-invalidations` (10 000) keys are queued, the node is flushed on recovery instead. That costs only that node's share of the cache.

**Bad values are not node failures.** A value that cannot be deserialised is evicted and treated as a miss. It never counts towards opening the circuit.

**Observability.** `/health` reports `cacheMode` (`FULL`, `DEGRADED` or `OFFLINE`) and per-node circuit state, failures, trips, recoveries and pending invalidations. `/metrics` reports `cacheMode`, `cacheDegradedReads`, `cacheShortCircuitedCalls`, `cacheDeferredInvalidations` and `cacheNodeStates`.

**Tests.** `RedisOutageIntegrationTest` runs the whole application (MockMvc, real Lettuce client, H2) against three in-process `FakeRedisServer`s that can be stopped and started. It checks four things: correct answers with every node down, healthy nodes keep hitting with one down, the hit rate returns to 100% after restart, and a healed partition never serves a stale count. `ResilientCacheClientTest` and `CircuitBreakerTest` cover the mechanics. Real-Redis drills are in `scripts/cluster_bench.py --phases outage`.
