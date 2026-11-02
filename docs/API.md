# QueryFlow REST API Documentation

QueryFlow exposes several REST endpoints for query autocompletion, search logging, system caching debugging, batch flushes monitoring, and observability metrics.

---

## 0. Rate Limiting (applies to `/suggest`, `/search`, `/trending`, `/trending/**`)

Each client IP has an independent token bucket per endpoint group (defaults: `suggest` 30 burst / 10 per s, `search` 10 / 2, `trending` 20 / 5; see `queryflow.rate-limit.*` in `application.properties`).

* Every limited response carries `X-RateLimit-Limit` (bucket capacity) and `X-RateLimit-Remaining`.
* When the bucket is empty the request is rejected before reaching the controller:
  ```http
  HTTP/1.1 429 Too Many Requests
  Retry-After: 1
  X-RateLimit-Limit: 30
  X-RateLimit-Remaining: 0
  Content-Type: application/json

  {"message":"Too many requests","endpoint":"suggest","retryAfterSeconds":1}
  ```
* Limits are enforced per backend instance (see ARCHITECTURE.md, section 9).

Every response also carries `X-QueryFlow-Instance`, the id of the backend that served it.

---

## 1. Core Endpoints

### Get Autocomplete Suggestions
Returns the top 10 search query suggestion records matching a prefix, sorted by score descending.

* **URL**: `/suggest`
* **Method**: `GET`
* **Parameters**:
  * `q` (string, required): The prefix query to match against.
* **Request Example**:
  `GET /suggest?q=ip`
* **Availability**: answered from PostgreSQL when the owning Redis node (or every node) is down, with the same content (see `cacheMode` under `/health`).
* **Response Example (200 OK)**:
  ```json
  [
    { "query": "iphone", "count": 12500 },
    { "query": "ipad", "count": 8400 }
  ]
  ```

---

### Record Search Submission
Submits a query to the in-memory write buffer. Counts are aggregated in RAM and persisted via a periodic batch flusher.

* **URL**: `/search`
* **Method**: `POST`
* **Headers**: `Content-Type: application/json`
* **Request Example**:
  `POST /search`
  ```json
  { "query": "iphone 18" }
  ```
* **Response Example (200 OK)**:
  ```json
  {
    "success": true,
    "message": "Search logged successfully"
  }
  ```

---

### Get Trending Searches
Returns the top 10 trending queries scored via the weighted popularity-recency ranking algorithm.

* **URL**: `/trending`
* **Method**: `GET`
* **Response Example (200 OK)**:
  ```json
  [
    { "query": "playstation 5", "count": 9200, "score": 0.85 },
    { "query": "iphone", "count": 12500, "score": 0.72 }
  ]
  ```

---

### Explain Trending Query Score
Details the raw metrics and calculated popularity vs. recency components for a query.

* **URL**: `/trending/explain`
* **Method**: `GET`
* **Parameters**:
  * `query` (string, required): The search query to explain.
* **Request Example**:
  `GET /trending/explain?query=iphone`
* **Response Example (200 OK)**:
  ```json
  {
    "query": "iphone",
    "count": 12500,
    "hoursSinceLastSearch": 0.25,
    "popularityScore": 0.95,
    "recencyScore": 0.8,
    "finalScore": 0.91
  }
  ```

---

## 2. Observability & Diagnostics

### Get Global System Metrics
Provides telemetry for API traffic, cache hit rates, DB reads/writes, write flushes, cache resilience and rate limiting. **All counters are for the instance named in `instanceId`**; sum them across instances for cluster totals. `databaseWrites` counts batch write transactions (one per non-empty flush).

* **URL**: `/metrics`
* **Method**: `GET`
* **Response Example (200 OK)**:
  ```json
  {
    "instanceId": "backend-1",
    "suggestionRequests": 1200,
    "trendingRequests": 250,
    "cacheHits": 980,
    "cacheMisses": 220,
    "cacheHitRate": 81.67,
    "databaseReads": 450,
    "databaseWrites": 180,
    "batchFlushes": 35,
    "averageFlushDurationMs": 120.0,
    "cacheInvalidations": 430,
    "cacheMode": "DEGRADED",
    "cacheDegradedReads": 57,
    "cacheShortCircuitedCalls": 51,
    "cacheDeferredInvalidations": 4,
    "cacheNodeStates": { "redis-node-1": "CLOSED", "redis-node-2": "OPEN", "redis-node-3": "CLOSED" },
    "rateLimitAllowed": 1450,
    "rateLimitRejected": 12,
    "rateLimitTrackedBuckets": 37,
    "rateLimitByEndpoint": {
      "suggest": { "allowed": 1200, "rejected": 12 },
      "search": { "allowed": 0, "rejected": 0 },
      "trending": { "allowed": 250, "rejected": 0 }
    }
  }
  ```

  | Field | Meaning |
  |---|---|
  | `cacheMode` | `FULL` (all node circuits closed), `DEGRADED` (some open), `OFFLINE` (none closed) |
  | `cacheDegradedReads` | Reads answered from PostgreSQL because the owning Redis node was unavailable |
  | `cacheShortCircuitedCalls` | Redis calls skipped because a node's circuit was open (no network time spent) |
  | `cacheDeferredInvalidations` | Cache DELs that could not reach their node and were queued for replay on recovery |
  | `rateLimit*` | Requests allowed / rejected with 429 by the rate limiter, and live client buckets |

---

### Get Endpoint Latency Metrics
Exposes average and maximum response latency for main suggestion and trending endpoints.

* **URL**: `/metrics/latency`
* **Method**: `GET`
* **Response Example (200 OK)**:
  ```json
  {
    "suggestionAvgMs": 12.4,
    "suggestionMaxMs": 55,
    "trendingAvgMs": 15.1,
    "trendingMaxMs": 70
  }
  ```

---

### Get Performance Benchmark Report
Compares system performance with vs. without optimizations.

* **URL**: `/benchmark/report`
* **Method**: `GET`
* **Response Example (200 OK)**:
  ```json
  {
    "cacheHitRate": 82.3,
    "estimatedDbReadsSaved": 980,
    "estimatedWriteReduction": 91.4,
    "batchFlushes": 35
  }
  ```

---

### Get Project Demo Summary
Aggregated telemetry designed as a single-endpoint summary for technical vivas and project demos.

* **URL**: `/demo/summary`
* **Method**: `GET`
* **Response Example (200 OK)**:
  ```json
  {
    "datasetQueries": 100000,
    "cacheNodes": 3,
    "cacheHitRate": 82.3,
    "batchFlushes": 35,
    "trendingQueries": 10,
    "systemStatus": "HEALTHY"
  }
  ```

---

### Health Check Status
Provides live connectivity status of Postgres DB, count of active Redis nodes on the consistent hash ring, and status of the ring topology.

* **URL**: `/health`
* **Method**: `GET`
* **Response Example (200 OK)**:
  ```json
  {
    "status": "UP",
    "instanceId": "backend-1",
    "database": "CONNECTED",
    "redisNodes": 3,
    "cacheRing": "ACTIVE",
    "cacheMode": "FULL",
    "cacheNodes": [
      { "nodeId": "redis-node-1", "host": "localhost", "port": 6379, "state": "CLOSED",
        "failures": 0, "shortCircuited": 0, "trips": 0, "recoveries": 0,
        "pendingInvalidations": 0, "flushOnRecovery": false }
    ]
  }
  ```
  `status` stays `UP` while Redis is down: suggestions keep working from PostgreSQL. `cacheNodes` lists all three nodes (one shown).

---

## 3. Cache & Buffer Debugging

* `GET /cache/stats`: Basic cache hits, misses, and hit rate.
* `GET /cache/ring`: Raw coordinate mappings of active Redis nodes.
* `GET /cache/distribution`: Distribution of keys cached per Redis node (`-1` = node unreachable or its circuit is open).
* `GET /cache/debug?prefix=..`: Which node a prefix routes to, that node's circuit state, and whether the key is cached.
* `GET /batch/status`: Current count of items in the in-memory write buffer and the last flush timestamp.
