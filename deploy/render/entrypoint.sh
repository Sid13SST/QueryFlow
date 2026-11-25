#!/bin/sh
# Starts three local Redis nodes, waits until each answers PING, then replaces this
# shell with the JVM (exec), so the JVM is PID 1 and receives Render's SIGTERM directly.
set -eu

REDIS_PORTS="${REDIS_PORTS:-6379 6380 6381}"
REDIS_MAXMEMORY="${REDIS_MAXMEMORY:-16mb}"

for port in $REDIS_PORTS; do
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
    --dir /tmp/redis \
    --logfile "/tmp/redis/redis-$port.log" \
    --daemonize yes
done

for port in $REDIS_PORTS; do
  tries=0
  until [ "$(redis-cli -p "$port" ping 2>/dev/null)" = "PONG" ]; do
    tries=$((tries + 1))
    if [ "$tries" -ge 50 ]; then
      echo "redis on port $port did not start:" >&2
      cat "/tmp/redis/redis-$port.log" >&2 || true
      exit 1
    fi
    sleep 0.2
  done
  echo "redis node on 127.0.0.1:$port is up (maxmemory $REDIS_MAXMEMORY)"
done

# Use the AppCDS archive built into the image if present. If JAVA_OPTS changes flags the
# archive was not built for, the JVM silently starts without it (-Xshare:auto).
cd /app/app
CDS_OPTS=""
if [ -f queryflow.jsa ]; then
  CDS_OPTS="-XX:SharedArchiveFile=queryflow.jsa -Xlog:cds=off -Xlog:cds+dynamic=off"
fi

# JAVA_OPTS and CDS_OPTS are intentionally unquoted: each holds several flags.
# shellcheck disable=SC2086
exec java $JAVA_OPTS $CDS_OPTS -jar queryflow.jar
