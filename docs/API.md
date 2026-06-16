# QueryFlow REST API Documentation

QueryFlow exposes several REST endpoints for query autocompletion, search logging, system caching debugging, batch flushes monitoring, and observability metrics.

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
Provides telemetry for API traffic, cache hit rates, DB reads/writes, and write flushes.

* **URL**: `/metrics`
* **Method**: `GET`
* **Response Example (200 OK)**:
  ```json
  {
    "suggestionRequests": 1200,
    "trendingRequests": 250,
    "cacheHits": 980,
    "cacheMisses": 220,
    "cacheHitRate": 81.67,
    "databaseReads": 450,
    "databaseWrites": 180,
    "batchFlushes": 35,
    "averageFlushDurationMs": 120.0,
    "cacheInvalidations": 430
  }
  ```

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
    "database": "CONNECTED",
    "redisNodes": 3,
    "cacheRing": "ACTIVE"
  }
  ```

---

## 3. Cache & Buffer Debugging

* `GET /cache/stats`: Basic cache hits, misses, and hit rate.
* `GET /cache/ring`: Raw coordinate mappings of active Redis nodes.
* `GET /cache/distribution`: Distribution of keys cached per Redis node.
* `GET /batch/status`: Current count of items in the in-memory write buffer and the last flush timestamp.
