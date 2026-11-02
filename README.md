# QueryFlow

QueryFlow is a high-performance distributed search typeahead system that delivers real-time autocomplete suggestions and trending search rankings. It combines distributed Redis caching with consistent hashing, in-memory batch write aggregation, popularity–recency trending algorithms, and active cache invalidation to handle high-throughput search traffic efficiently.

---

## Features

| Phase | Feature | Description |
|---|---|---|
| 0 | Project Setup | Spring Boot + React (Vite) boilerplate, health check, global exception handling |
| 1 | Dataset Loading | CSV dataset auto-loading into PostgreSQL on startup |
| 2 | Suggestion API | Prefix-based autocomplete with case-insensitive matching |
| 3 | Search Submission | POST /search endpoint with count tracking |
| 4 | Redis Cache | Single-node Redis caching for suggestions |
| 5 | Distributed Cache | 3-node Redis cluster with consistent hashing & virtual nodes |
| 6 | Trending Searches | Top trending queries by search count |
| 7 | Batch Writes | In-memory buffer + scheduled flush: one write transaction per flush instead of one per search |
| 8 | Advanced Trending | Popularity + recency decay scoring with configurable weights |
| 9 | Cache Invalidation | Active prefix-based invalidation after batch flushes |
| 10 | Metrics & Observability | Real-time metrics dashboard, latency tracking, benchmark reports |
| 11 | Rate Limiting | Per-client-IP token buckets on `/suggest`, `/search`, `/trending`; HTTP 429 + `Retry-After`; configurable; metrics |
| 12 | Redis-Outage Resilience | Per-node circuit breakers, PostgreSQL fallback, automatic recovery, no stale reads after a partition heals |
| 13 | Multi-Instance Readiness | Atomic batch increments, instance ids, 2 backends + nginx via docker-compose, reproducible cluster benchmark |

---

## Architecture Overview

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

In front of this, a rate-limit filter guards the public endpoints. Every Redis call goes through a per-node circuit breaker: if a node is down, its keys are served from PostgreSQL and the other nodes keep serving theirs. Any number of identical backends can run behind a load balancer (see [Scaling & Reliability](#scaling--reliability)).

---

## Tech Stack

### Backend
* **Java 17+** (built and tested on JDK 21) with **Spring Boot 4.1** (Maven)
* **Spring Data JPA** + **PostgreSQL**
* **Spring Data Redis** (3-node distributed cache)
* **Lombok** for boilerplate reduction

### Frontend
* **React 18** (Vite)
* **Axios** (Centralized API client)
* **Tailwind CSS v4**

### Infrastructure
* **PostgreSQL** — Persistent storage
* **Redis** × 3 — Distributed cache nodes (ports 6379, 6380, 6381)
* **nginx** — Load balancer for the 2-instance cluster (`docker-compose.cluster.yml`)

---

## Project Structure

```text
QueryFlow/
├── backend/
│   ├── src/main/java/com/queryflow/
│   │   ├── cache/             # ResilientCacheClient, per-node circuit breakers
│   │   ├── config/            # Redis config, instance id filter, exception handlers
│   │   ├── ratelimit/         # Token buckets, rate-limit filter and properties
│   │   ├── controller/        # REST endpoints (12 controllers)
│   │   ├── dto/               # Response DTOs (16 classes)
│   │   ├── entity/            # JPA entities (SearchQuery)
│   │   ├── repository/        # Spring Data repositories
│   │   ├── service/           # Business logic (12 services)
│   │   └── QueryFlowApplication.java
│   ├── src/test/java/         # Unit & integration tests (74 tests)
│   ├── Dockerfile             # Runtime image (copies the built jar)
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── api/               # Axios HTTP client
│   │   ├── components/        # SearchBar, TrendingSearches, DeveloperDashboard
│   │   ├── App.jsx            # Main application layout
│   │   └── index.css          # Tailwind CSS
│   └── package.json
│
├── deploy/nginx.conf          # Load balancer config for the cluster
├── scripts/                   # Cluster benchmark & Redis outage drill
├── docker-compose.yml         # 3 Redis nodes for local development
├── docker-compose.cluster.yml # 2 backends + nginx + PostgreSQL + 3 Redis
├── BASELINE.md                # Test baseline before the reliability work
│
└── docs/
    ├── ARCHITECTURE.md        # System design & component diagrams
    ├── API.md                 # Full API reference
    └── VIVA_NOTES.md          # Interview/viva preparation Q&A
```

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | System health, DB status, cache mode and per-node circuit state |
| `GET` | `/suggest?q={prefix}` | Autocomplete suggestions (top 10) |
| `POST` | `/search` | Submit a search query |
| `GET` | `/trending` | Top trending searches (scored) |
| `GET` | `/trending/explain?query={q}` | Score breakdown for a query |
| `GET` | `/metrics` | Per-instance metrics incl. cache resilience and rate limiting |
| `GET` | `/metrics/latency` | Suggestion & trending latency stats |
| `GET` | `/benchmark/report` | Cache hit rate & write reduction report |
| `GET` | `/demo/summary` | Combined overview for demos |
| `GET` | `/cache/stats` | Cache hit/miss counters |
| `GET` | `/cache/invalidation/stats` | Cache invalidation counters |
| `GET` | `/batch/status` | Batch writer buffer status |
| `GET` | `/batch/toggle-outage` | Toggle a simulated DB outage for the batch writer (demo only) |

`/suggest`, `/search` and `/trending/**` are rate limited per client IP; over the limit they return `429 Too Many Requests` with `Retry-After`. Every response carries `X-QueryFlow-Instance`.

See [docs/API.md](docs/API.md) for full request/response examples.

---

## Prerequisites

1. **JDK 17+** (tested on 21)
2. **Node.js 18+** and npm
3. **PostgreSQL** running locally
4. **Redis** — 3 instances on ports 6379, 6380, 6381

### Redis Setup (Windows)

Start three Redis instances:
```bash
redis-server --port 6379
redis-server --port 6380
redis-server --port 6381
```

### Database Setup

```sql
CREATE DATABASE queryflow;
```

Copy `backend/.env.example` to `backend/.env` and set your credentials:
```env
DB_URL=jdbc:postgresql://localhost:5432/queryflow
DB_USERNAME=postgres
DB_PASSWORD=your_password
```

---

## Quick Start

### Backend

```bash
cd backend
.\mvnw.cmd clean compile       # Windows
.\mvnw.cmd spring-boot:run     # Start on port 8080
```

### Frontend

```bash
cd frontend
npm install
npm run dev                    # Start on port 5173
```

### Verify

```bash
curl http://localhost:8080/health
# → {"status":"UP","service":"QueryFlow","database":"Connected",...}
```

Open http://localhost:5173 in your browser.

---

## Running Tests

```bash
cd backend
.\mvnw.cmd test        # Windows
./mvnw test            # Linux / macOS (or: mvn test)
```

**74 tests** (73 pass, 1 disabled context test; the baseline was 35, see [BASELINE.md](BASELINE.md)). No PostgreSQL, Redis or Docker is needed: full-context tests use H2 and an in-process fake Redis that can be stopped and started. To run the same tests against a real PostgreSQL:

```bash
QUERYFLOW_TEST_JDBC_URL=jdbc:postgresql://localhost:5432/queryflow_test ./mvnw test
```

Coverage:
- `SearchServiceTest` — Search submission & buffering
- `SuggestionServiceTest` — Cache hit/miss/fallback paths
- `TrendingServiceTest` — Score calculation & ranking
- `BatchWriteSystemTest` — Buffer aggregation & flush
- `CacheInvalidationServiceTest` — Prefix generation & eviction
- `MetricsIntegrationTest` — Cross-service metrics collection
- `TokenBucketTest`, `RateLimiterServiceTest`, `RateLimitWebTest`, `RateLimitConfigurationTest` — 429/Retry-After, per-IP isolation, config/env overrides, metrics
- `CircuitBreakerTest`, `ResilientCacheClientTest` — breaker states, one/all nodes down, recovery, invalidation replay
- `RedisOutageIntegrationTest` — whole app with Redis stopped: correct answers, hit-rate recovery, no stale reads
- `MultiInstanceBatchWriteTest` — two writers flushing concurrently: no lost or double-counted searches

---

## Key Design Decisions

### Consistent Hashing
MD5-based hash ring with 100 virtual nodes per physical Redis node ensures uniform key distribution. If a node fails, only its keys are redistributed.

### Batch Write Aggregation
`ConcurrentHashMap.merge()` accumulates search counts in memory. A scheduled task flushes every 30 seconds in a single transaction, using atomic `count = count + delta` updates so several instances can flush concurrently. The write reduction depends on traffic × flush interval. In the cluster benchmark, 2000 searches produced 4 write transactions (99.8% fewer).

### Trending Score Formula
```
score = 0.7 × (count / maxCount) + 0.3 × (1 / (hoursSinceLastSearch + 1))
```
Configurable weights in `application.properties`. Prevents stale queries from permanently dominating.

### Active Cache Invalidation
After each batch flush, all affected prefix keys (`suggest:i`, `suggest:ip`, ...) and `trending:top` are routed through the hash ring and deleted from the correct Redis nodes.

### Redis Outage Handling
Each Redis node has its own circuit breaker. A node that keeps failing is skipped (no timeouts) and its keys are read from PostgreSQL. It is probed again after 5 s and resumes automatically. Invalidations it missed are replayed before it serves reads again.

### Rate Limiting
A token bucket per (endpoint, client IP), held in memory on each instance so it keeps working when Redis is down. Behind a proxy the client IP is the right-most `X-Forwarded-For` entry.

---

## Scaling & Reliability

Run two backends behind nginx, sharing PostgreSQL and the Redis ring:

```bash
scripts/run-cluster-bench.sh          # builds, starts the cluster, runs the benchmark
```

This uses only free tools (Docker, Python 3 standard library). Measured on a 4-vCPU Linux container; details and caveats are in [docs/ARCHITECTURE.md §9](docs/ARCHITECTURE.md#9-horizontal-scaling-2-instances):

| Scenario | Result |
|---|---|
| 5000 `/suggest`, 16 concurrent, cold cache | 2500 / 2500 per instance, 0 errors, 98.6% cluster hit rate (one miss per distinct prefix across both instances) |
| 2000 concurrent `/search` via both instances | PostgreSQL counts exact (+1000 / +1000) |
| 1 of 3 Redis nodes stopped | 0 errors, 0 wrong answers, 57.4% hit rate |
| All Redis nodes stopped | 0 errors, 0 wrong answers (served from PostgreSQL) |
| Redis back (empty) | `cacheMode` FULL after 5.9 s; hit rate back to 100% on the second pass |
| 100-request burst from one client | 72 allowed, 28 rejected with 429 (limits are per instance) |

Still instance-local by design: the write buffer, `/metrics` counters (sum across instances), rate-limit buckets and circuit state. The reasons are documented in ARCHITECTURE.md.

---

## Configuration

All settings are externalized in `application.properties` with environment variable overrides:

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Backend port |
| `queryflow.batch.flush-interval-seconds` | `30` | Batch flush frequency |
| `queryflow.trending.limit` | `10` | Number of trending results |
| `queryflow.trending.popularity-weight` | `0.7` | Popularity score weight |
| `queryflow.trending.recency-weight` | `0.3` | Recency score weight |
| `queryflow.trending.candidate-limit` | `500` | Candidate pool size |
| `queryflow.trending.cache.ttl-minutes` | `5` | Cache TTL safety net |
| `queryflow.instance-id` | `$INSTANCE_ID` / hostname | Instance name in `/metrics`, `/health`, `X-QueryFlow-Instance` |
| `queryflow.rate-limit.enabled` | `true` | Master switch (`RATE_LIMIT_ENABLED`) |
| `queryflow.rate-limit.trust-forwarded-header` | `false` | Use right-most `X-Forwarded-For` as client IP (`RATE_LIMIT_TRUST_FORWARDED`) |
| `queryflow.rate-limit.endpoints.suggest.capacity` / `.refill-per-second` | `30` / `10` | `/suggest` burst / sustained rate |
| `queryflow.rate-limit.endpoints.search.capacity` / `.refill-per-second` | `10` / `2` | `/search` burst / sustained rate |
| `queryflow.rate-limit.endpoints.trending.capacity` / `.refill-per-second` | `20` / `5` | `/trending/**` burst / sustained rate |
| `queryflow.cache.circuit-breaker.failure-threshold` | `3` | Consecutive Redis errors before a node's circuit opens |
| `queryflow.cache.circuit-breaker.open-duration-ms` | `5000` | How long an open node is skipped before a probe |
| `queryflow.cache.circuit-breaker.probe-interval-ms` | `2000` | Background probe frequency for open circuits |
| `queryflow.cache.redis.command-timeout-ms` | `500` | Per-command Redis timeout |

---

## Documentation

* [Architecture](docs/ARCHITECTURE.md) — System design, diagrams, component details
* [API Reference](docs/API.md) — Full endpoint documentation
* [Viva Notes](docs/VIVA_NOTES.md) — Interview Q&A preparation
