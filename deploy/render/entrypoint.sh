#!/bin/sh
# Starts three local Redis nodes, waits until each answers PING, then replaces this
# shell with the JVM (exec), so the JVM is PID 1 and receives Render's SIGTERM directly.
#
# Nothing here relies on directories baked into the image under /tmp: hosts such as
# Render mount a fresh, empty /tmp at runtime, which hides anything the image put there.
# The working directory is chosen (and created) at start-up instead.
set -eu

log() { echo "[entrypoint] $*"; }
fail() { echo "[entrypoint] ERROR: $*" >&2; exit 1; }

REDIS_PORTS="${REDIS_PORTS:-6379 6380 6381}"
REDIS_MAXMEMORY="${REDIS_MAXMEMORY:-16mb}"
REDIS_LOGLEVEL="${REDIS_LOGLEVEL:-warning}"

# ---------------------------------------------------------------------------
# 1. A writable runtime directory (Redis working dir + JVM temp dir).
#    First candidate that can be created and written to wins. /app/run is created
#    in the image and owned by the runtime user, so it works even if /tmp is
#    read-only or missing.
# ---------------------------------------------------------------------------
pick_runtime_dir() {
  for candidate in "${QUERYFLOW_RUNTIME_DIR:-}" "${TMPDIR:-/tmp}/queryflow" /app/run; do
    [ -n "$candidate" ] || continue
    if mkdir -p "$candidate" 2>/dev/null && [ -w "$candidate" ] \
       && touch "$candidate/.write-test" 2>/dev/null; then
      rm -f "$candidate/.write-test"
      echo "$candidate"
      return 0
    fi
  done
  return 1
}

if RUNTIME_DIR="$(pick_runtime_dir)"; then
  log "runtime dir: $RUNTIME_DIR (user $(id -un 2>/dev/null || id -u))"
else
  # Tomcat must create its work directory, so a fully read-only filesystem cannot work.
  # Say so now instead of failing later inside Spring with a less obvious error.
  fail "no writable directory for runtime files (tried \$QUERYFLOW_RUNTIME_DIR, ${TMPDIR:-/tmp}/queryflow, /app/run) as user $(id -u); mount a writable /tmp or set QUERYFLOW_RUNTIME_DIR"
fi

# ---------------------------------------------------------------------------
# 2. Redis nodes. Run in the foreground as background jobs (not --daemonize) so
#    their messages go to the container log, where Render shows them.
# ---------------------------------------------------------------------------
command -v redis-server >/dev/null 2>&1 || fail "redis-server is not installed in the image"
command -v redis-cli >/dev/null 2>&1 || fail "redis-cli is not installed in the image"

PIDS=""
i=0
for port in $REDIS_PORTS; do
  i=$((i + 1))
  node_dir="$RUNTIME_DIR/redis-$port"
  mkdir -p "$node_dir" 2>/dev/null || node_dir="$RUNTIME_DIR"
  # Cache only: no RDB/AOF persistence, LRU eviction at a small cap, loopback only.
  redis-server \
    --port "$port" \
    --bind 127.0.0.1 \
    --protected-mode yes \
    --maxmemory "$REDIS_MAXMEMORY" \
    --maxmemory-policy allkeys-lru \
    --save '' \
    --appendonly no \
    --databases 1 \
    --dir "$node_dir" \
    --logfile '' \
    --loglevel "$REDIS_LOGLEVEL" \
    --daemonize no &
  PIDS="$PIDS $!"
  log "starting redis-node-$i on 127.0.0.1:$port (pid $!, maxmemory $REDIS_MAXMEMORY)"
done

# Wait for every node; if one exits or never answers, say which and stop the container,
# so the failure is visible in the deploy log instead of a half-working cache.
i=0
for port in $REDIS_PORTS; do
  i=$((i + 1))
  pid="$(echo $PIDS | cut -d' ' -f"$i")"
  tries=0
  until [ "$(redis-cli -p "$port" ping 2>/dev/null)" = "PONG" ]; do
    if ! kill -0 "$pid" 2>/dev/null; then
      fail "redis-node-$i (127.0.0.1:$port, pid $pid) exited during start-up; see its messages above"
    fi
    tries=$((tries + 1))
    if [ "$tries" -ge 100 ]; then
      fail "redis-node-$i (127.0.0.1:$port) did not answer PING within 20 s"
    fi
    sleep 0.2
  done
  log "redis-node-$i on 127.0.0.1:$port is up"
done

# ---------------------------------------------------------------------------
# 3. The JVM. Temp files (Tomcat work dir) go to the runtime dir; -XX:-UsePerfData
#    stops the JVM writing /tmp/hsperfdata_*, so a read-only /tmp is fine.
# ---------------------------------------------------------------------------
JVM_TMP_OPTS="-XX:-UsePerfData"
if [ -w "$RUNTIME_DIR" ]; then
  mkdir -p "$RUNTIME_DIR/java-tmp"
  JVM_TMP_OPTS="$JVM_TMP_OPTS -Djava.io.tmpdir=$RUNTIME_DIR/java-tmp"
fi

# Use the AppCDS archive built into the image if present. If JAVA_OPTS changes flags the
# archive was not built for, the JVM silently starts without it (-Xshare:auto).
cd /app/app
CDS_OPTS=""
if [ -r queryflow.jsa ]; then
  CDS_OPTS="-XX:SharedArchiveFile=queryflow.jsa -Xlog:cds=off -Xlog:cds+dynamic=off"
fi

log "starting the API on port ${PORT:-8080}"
# JAVA_OPTS, JVM_TMP_OPTS and CDS_OPTS are intentionally unquoted: each holds several flags.
# shellcheck disable=SC2086
exec java $JAVA_OPTS $JVM_TMP_OPTS $CDS_OPTS -jar queryflow.jar
