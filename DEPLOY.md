# Free public demo deployment

Everything below runs on free plans (₹0 / $0, no card needed at the time of writing):

| Part | Where | What runs there |
|---|---|---|
| Frontend | **Vercel** (Hobby) | The Vite/React build in `frontend/` |
| Backend | **Render** free web service (Docker) | Spring Boot API **plus three real `redis-server` nodes** in one container (`Dockerfile.render`) |
| Database | **Neon** free PostgreSQL | `search_queries` table, created and filled from the bundled CSV on first start |

```
Browser ──> Vercel (static React app)
   │
   └── HTTPS ──> Render (Cloudflare edge -> Render proxy) ──> container
                                                                ├─ Spring Boot API  ($PORT)
                                                                ├─ redis-server 127.0.0.1:6379 ┐
                                                                ├─ redis-server 127.0.0.1:6380 ├ consistent-hash ring
                                                                └─ redis-server 127.0.0.1:6381 ┘
                                                                         │ JDBC + SSL
                                                                         └──> Neon PostgreSQL
```

No mock data and no fake mode: the deployed page talks to the real API, the real Redis ring and the real database.

---

## 1. Neon (PostgreSQL)

1. Sign up at <https://neon.tech> and create a project (Postgres 16 or 17). Pick the region closest to your Render region; `render.yaml` uses **Singapore**, which pairs with Neon **AWS Asia Pacific (Singapore) / ap-southeast-1**.
2. On the project dashboard open **Connect**. Turn **Connection pooling off** (the app has its own small pool, and a direct connection avoids PgBouncer transaction-mode limits). You get a string like
   ```
   postgresql://neondb_owner:AbCdEf123@ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require
   ```
3. Split it into the three values the backend reads:

   | Render env var | Value from the example |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USERNAME` | `neondb_owner` |
   | `DB_PASSWORD` | `AbCdEf123` |

   Note the `jdbc:` prefix and that the user and password are **not** in `DB_URL`. `sslmode=require` is mandatory for Neon.

You do not need to create tables: on first start Hibernate creates `search_queries` and the app loads `data/search_queries.csv`. Later starts skip rows that already exist, so restarts and redeploys never duplicate data or reset counts.

## 2. Render (backend)

1. Push this branch (or `main` once merged) to GitHub.
2. In <https://dashboard.render.com>: **New → Blueprint**, connect the repository, and select the branch. Render reads `render.yaml` and proposes one **free** web service, `queryflow-backend`, built from `Dockerfile.render`.
3. Render asks for the three secrets marked `sync: false`: paste `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` from step 1. The other variables are preset:
   * `RATE_LIMIT_TRUST_FORWARDED=true`, `RATE_LIMIT_CLIENT_IP_HEADER=CF-Connecting-IP` (see *Rate limiting* below)
   * `INSTANCE_ID=render-free`
   * `PORT` is injected by Render; the app listens on it.
4. **Apply**. The first build takes a few minutes (Maven build plus a class-data-sharing archive that speeds up later cold starts). Render then waits for `GET /health` to return 200.
5. Check it, replacing the host with yours:
   ```bash
   curl https://queryflow-backend.onrender.com/health
   ```
   Expect `"status":"UP"`, `"database":"CONNECTED"`, `"redisNodes":3`, `"cacheMode":"FULL"`.
   ```bash
   curl "https://queryflow-backend.onrender.com/suggest?q=ja"
   ```
   Expect suggestions from the CSV (`java`, ...).

Without the Blueprint you can create it by hand: **New → Web Service → Docker**, Dockerfile path `./Dockerfile.render`, Docker build context `.`, instance type **Free**, health check path `/health`, and the env vars above.

### Rate limiting behind Render

Every Render service sits behind Cloudflare, and Render's proxy appends the Cloudflare edge address to `X-Forwarded-For`. Keying the limiter on that address would let one busy visitor use up the limit for everyone on the same edge. The Blueprint therefore keys on `CF-Connecting-IP`, which Cloudflare overwrites on every request, so a visitor cannot spoof it.

Verify after deploying:
```bash
curl https://queryflow-backend.onrender.com/ratelimit/client
curl -H "X-Forwarded-For: 1.2.3.4" https://queryflow-backend.onrender.com/ratelimit/client
```
Both must show **your** public IP with `"source":"CLIENT_IP_HEADER"`; the forged `X-Forwarded-For` must not change it. A request that forges `CF-Connecting-IP` itself is rejected by Cloudflare with HTTP 403 before it reaches the app.

Default limits per visitor (token bucket, burst then sustained rate; over the limit returns HTTP 429 with `Retry-After`):

| Endpoint | Burst | Sustained |
|---|---|---|
| `/suggest` | 30 | 10 req/s |
| `/search` | 10 | 2 req/s |
| `/trending`, `/trending/**` | 20 | 5 req/s |

The frontend debounces typing by 300 ms, so a normal visitor never gets close. Change them with env vars such as `RATE_LIMIT_SUGGEST_CAPACITY`, `RATE_LIMIT_SUGGEST_RPS` (see README → Configuration). Health, metrics and dashboard endpoints are not limited.

## 3. Vercel (frontend)

1. In <https://vercel.com>: **Add New → Project**, import the repository.
2. **Root Directory:** `frontend`. Framework preset **Vite** is detected (build `npm run build`, output `dist`); no `vercel.json` is needed because the app has no client-side routes.
3. **Environment Variables:** `VITE_API_BASE_URL` = your Render URL, e.g. `https://queryflow-backend.onrender.com` (no trailing slash). It is baked in at build time, so redeploy after changing it.
4. **Deploy** and open the Vercel URL.

CORS: all API controllers allow any origin (`@CrossOrigin("*")`), including preflight for `POST /search` and the 429 responses, so the Vercel domain needs no backend change.

## 4. Honest limitations

* **Cold start.** A free Render instance sleeps after 15 minutes without inbound traffic. The next visitor's first request wakes it. Measured locally under the same limits (512 MB, 0.1 CPU): about 35 s from container start to a healthy API; on Render add the time to pull the image and start the container, so expect up to about a minute. The page shows a "Waking up the backend" notice with a timer, loads everything as soon as the API answers, and offers **Retry now** if it takes over 150 s.
* **Slow CPU.** 0.1 CPU is enough for a demo, not for load. In a local run of 3,000 mixed requests with 16 concurrent clients, p50 latency was about 300 ms and p95 about 1.4 s; a single visitor sees much faster responses. Batch flushes take longer than on a laptop.
* **Memory.** Measured under a 512 MB limit after start-up and those 3,000 requests: container peak **268 MiB** (java RSS ~282 MiB, each Redis ~8 MiB; RSS counts shared pages more than once). Heap is capped at 224 MB, each Redis node at 16 MB (LRU eviction), so the worst case still fits.
* **Cache is not persistent.** Redis runs inside the container without persistence, so the cache starts empty after every sleep or deploy and warms up again from PostgreSQL. Searches waiting in the write buffer are flushed every 30 s; a crash (not a normal stop) can lose up to that window.
* **Neon scales to zero** after a few minutes idle; the first query afterwards waits for it to resume (usually under a few seconds; the pool's connect timeout is 20 s). Free projects have storage and compute-hour caps, far above what this demo uses.
* **Render free limits:** a monthly cap on free instance hours (one always-awake service fits) and on build minutes; no SSH or persistent disk. Plans change, so check the current free tier pages of Render, Neon and Vercel.
* **One instance.** The multi-backend nginx setup (`docker-compose.cluster.yml`) is for local benchmarks; the free demo runs one backend, so `X-QueryFlow-Instance` is always `render-free`.

## 5. Troubleshooting the Render service

The container log on Render starts with `[entrypoint]` lines:

* `runtime dir: /tmp/queryflow (user queryflow)`: where Redis and the JVM keep runtime files. It is created at start-up, because Render mounts a fresh, empty `/tmp` that hides anything the image put there. If `/tmp` is not writable, `/app/run` (created in the image for the runtime user) is used instead.
* `redis-node-N on 127.0.0.1:PORT is up` for all three nodes, then `starting the API on port 10000`.
* If a node cannot start, the container stops with `ERROR: redis-node-N (127.0.0.1:PORT, pid ...) exited during start-up`, and Redis's own reason is printed just above it.
* `WARNING Memory overcommit must be enabled!` from Redis is expected and harmless: it concerns background saves, which are disabled here (no persistence).

The runtime image is pinned to `eclipse-temurin:21-jre-resolute` (Ubuntu 26.04, `redis-server` 8.0), so a moving base tag cannot change the Redis major version under the deployment.

## 6. Verified on a live Render free instance (2026-11-25)

Commit `3c6c27d` was deployed with the Blueprint to a free Render web service (`https://queryflow-api.onrender.com`) backed by Neon. Checks on the live host:

* `/health`: `"status":"UP"`, `"database":"CONNECTED"`, all 3 Redis nodes in state `CLOSED`.
* `/suggest`, `POST /search` and `/trending` return real data.
* A forged `X-Forwarded-For` does not change the client address reported by `/ratelimit/client`.
* A forged `CF-Connecting-IP` header is rejected by Cloudflare with HTTP 403 before it reaches the app.
* A burst of 45 `POST /search` requests got 10 × 200, then 429 (the `/search` burst limit is 10).

The first deploy failed before the API started: Redis would not start because its working directory (`/tmp/redis`, created in the image) did not exist in the running container. Render mounts a fresh `/tmp` at runtime. This only showed up on Render, not in the local Docker runs, and is fixed in `3c6c27d` (runtime directories are created by the entrypoint at start-up; see section 5).

## 7. Run the Render image locally

```bash
docker build -f Dockerfile.render -t queryflow-render .
```
```bash
docker run --rm -p 10000:10000 --memory 512m --cpus 0.1 \
  -e DB_URL='jdbc:postgresql://host.docker.internal:5432/queryflow' \
  -e DB_USERNAME=postgres -e DB_PASSWORD=postgres \
  queryflow-render
```
Then open <http://localhost:10000/health>. Any PostgreSQL works; add `?sslmode=require` when the server has SSL (Neon does).
