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
| 7 | Batch Writes | In-memory buffer + scheduled flush to reduce DB write load by ~97% |
| 8 | Advanced Trending | Popularity + recency decay scoring with configurable weights |
| 9 | Cache Invalidation | Active prefix-based invalidation after batch flushes |
| 10 | Metrics & Observability | Real-time metrics dashboard, latency tracking, benchmark reports |

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

---

## Tech Stack

### Backend
* **Java 23** with **Spring Boot 3** (Maven)
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

---

## Project Structure

```text
QueryFlow/
├── backend/
│   ├── src/main/java/com/queryflow/
│   │   ├── config/            # Redis config, CORS, exception handlers
│   │   ├── controller/        # REST endpoints (12 controllers)
│   │   ├── dto/               # Response DTOs (16 classes)
│   │   ├── entity/            # JPA entities (SearchQuery)
│   │   ├── repository/        # Spring Data repositories
│   │   ├── service/           # Business logic (12 services)
│   │   └── QueryFlowApplication.java
│   ├── src/test/java/         # Unit & integration tests (35 tests)
│   ├── data/                  # search_queries.csv dataset
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
└── docs/
    ├── ARCHITECTURE.md        # System design & component diagrams
    ├── API.md                 # Full API reference
    └── VIVA_NOTES.md          # Interview/viva preparation Q&A
```

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/health` | System health with DB & Redis status |
| `GET` | `/suggest?q={prefix}` | Autocomplete suggestions (top 10) |
| `POST` | `/search` | Submit a search query |
| `GET` | `/trending` | Top trending searches (scored) |
| `GET` | `/trending/explain?query={q}` | Score breakdown for a query |
| `GET` | `/metrics` | Real-time system metrics |
| `GET` | `/metrics/latency` | Suggestion & trending latency stats |
| `GET` | `/benchmark/report` | Cache hit rate & write reduction report |
| `GET` | `/demo/summary` | Combined overview for demos |
| `GET` | `/cache/stats` | Cache hit/miss counters |
| `GET` | `/cache/invalidation/stats` | Cache invalidation counters |
| `GET` | `/batch/status` | Batch writer buffer status |
| `POST` | `/batch/flush` | Force immediate batch flush |

See [docs/API.md](docs/API.md) for full request/response examples.

---

## Prerequisites

1. **Java 23** (JDK)
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
.\mvnw.cmd test
```

All **35 tests** should pass covering:
- `SearchServiceTest` — Search submission & buffering
- `SuggestionServiceTest` — Cache hit/miss/fallback paths
- `TrendingServiceTest` — Score calculation & ranking
- `BatchWriteSystemTest` — Buffer aggregation & flush
- `CacheInvalidationServiceTest` — Prefix generation & eviction
- `MetricsIntegrationTest` — Cross-service metrics collection

---

## Key Design Decisions

### Consistent Hashing
MD5-based hash ring with 100 virtual nodes per physical Redis node ensures uniform key distribution. If a node fails, only its keys are redistributed.

### Batch Write Aggregation
`ConcurrentHashMap.merge()` accumulates search counts in memory. A scheduled task flushes every 30 seconds via a single bulk DB transaction, reducing write load by ~97%.

### Trending Score Formula
```
score = 0.7 × (count / maxCount) + 0.3 × (1 / (hoursSinceLastSearch + 1))
```
Configurable weights in `application.properties`. Prevents stale queries from permanently dominating.

### Active Cache Invalidation
After each batch flush, all affected prefix keys (`suggest:i`, `suggest:ip`, ...) and `trending:top` are routed through the hash ring and deleted from the correct Redis nodes.

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

---

## Documentation

* [Architecture](docs/ARCHITECTURE.md) — System design, diagrams, component details
* [API Reference](docs/API.md) — Full endpoint documentation
* [Viva Notes](docs/VIVA_NOTES.md) — Interview Q&A preparation
