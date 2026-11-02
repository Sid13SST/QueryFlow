#!/usr/bin/env bash
# End-to-end, reproducible cluster benchmark (free tools only: JDK, Docker, Python 3).
#
#   scripts/run-cluster-bench.sh            # build, start, benchmark, leave cluster running
#   scripts/run-cluster-bench.sh --down     # ... and tear everything down at the end
#
# 1. Builds the backend jar and starts docker-compose.cluster.yml
#    (2 backends + nginx + PostgreSQL + 3 Redis nodes).
# 2. Rate-limit phase with the default limits enabled.
# 3. Restarts the backends with rate limiting disabled and a 5 s flush interval, then runs
#    the traffic, writes and outage phases (limits off so the load test measures the cache
#    path, not the limiter).
# Results are printed as JSON and saved to bench-results/cluster-<timestamp>.json.
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -f docker-compose.cluster.yml)
OUT_DIR=bench-results
mkdir -p "$OUT_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)

echo "== building backend jar"
if [ -f backend/mvnw ] && [ -z "${USE_SYSTEM_MAVEN:-}" ]; then
  (cd backend && sh ./mvnw -q -B -DskipTests package)
else
  (cd backend && mvn -q -B -DskipTests package)
fi

wait_healthy() {
  for _ in $(seq 1 120); do
    if curl -fs http://localhost:8080/health >/dev/null 2>&1 \
       && curl -fs http://localhost:8081/health >/dev/null 2>&1 \
       && curl -fs http://localhost:8082/health >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "cluster did not become healthy" >&2
  "${COMPOSE[@]}" ps >&2
  return 1
}

echo "== starting cluster (rate limiting ON, default limits)"
RATE_LIMIT_ENABLED=true "${COMPOSE[@]}" up -d --build
wait_healthy
python3 scripts/cluster_bench.py --phases ratelimit --json-out "$OUT_DIR/cluster-$STAMP-ratelimit.json"

echo "== restarting backends (rate limiting OFF, 5 s batch flush) for load/outage phases"
RATE_LIMIT_ENABLED=false BATCH_FLUSH_INTERVAL=5 "${COMPOSE[@]}" up -d
wait_healthy
python3 scripts/cluster_bench.py --phases traffic,writes,outage --json-out "$OUT_DIR/cluster-$STAMP.json"

echo "== results saved in $OUT_DIR/cluster-$STAMP*.json"
if [ "${1:-}" = "--down" ]; then
  "${COMPOSE[@]}" down -v
fi
