#!/usr/bin/env python3
"""
QueryFlow cluster benchmark: 2 backends behind nginx, shared PostgreSQL + 3-node Redis ring.

Standard library only. Talks HTTP to nginx (default :8080) and to each backend directly
(default :8081, :8082) to read per-instance /metrics, and drives Redis outages with
`docker compose stop/start`.

Phases (run any subset, in this order):
  ratelimit  burst one client at /suggest and count 200 vs 429 (needs RATE_LIMIT_ENABLED=true)
  traffic    concurrent /suggest load: per-instance distribution, latency, cluster hit rate
  writes     concurrent POST /search through both instances; verify exact DB counts after flush
  outage     stop one Redis node, then all of them; verify answers stay correct; measure recovery

Example:
  python3 scripts/cluster_bench.py --phases traffic,writes,outage
scripts/run-cluster-bench.sh runs the whole thing end to end.
"""
import argparse
import concurrent.futures as cf
import json
import os
import statistics
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

DATASET_QUERIES = [
    "iphone", "iphone 15", "java", "python", "react", "typescript",
    "spring boot", "docker", "kubernetes", "postgresql",
]


def all_prefixes():
    seen, out = set(), []
    for q in DATASET_QUERIES:
        for i in range(1, len(q) + 1):
            p = q[:i].strip()
            if p and p not in seen:
                seen.add(p)
                out.append(p)
    return out


PREFIXES = all_prefixes()


class Http:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def request(self, method, path, body=None, timeout=10):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        if data is not None:
            req.add_header("Content-Type", "application/json")
        start = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                payload = resp.read()
                status, headers = resp.status, resp.headers
        except urllib.error.HTTPError as e:
            payload, status, headers = e.read(), e.code, e.headers
        elapsed_ms = (time.perf_counter() - start) * 1000
        return status, headers, payload, elapsed_ms

    def json(self, path):
        status, _, payload, _ = self.request("GET", path)
        if status != 200:
            raise RuntimeError(f"GET {path} -> {status}: {payload[:200]!r}")
        return json.loads(payload)


def pct(values, p):
    if not values:
        return 0.0
    s = sorted(values)
    k = max(0, min(len(s) - 1, int(round(p / 100.0 * (len(s) - 1)))))
    return s[k]


def summarize_latency(values):
    return {
        "p50_ms": round(pct(values, 50), 2),
        "p95_ms": round(pct(values, 95), 2),
        "p99_ms": round(pct(values, 99), 2),
        "mean_ms": round(statistics.fmean(values), 2) if values else 0.0,
    }


class Cluster:
    def __init__(self, args):
        self.lb = Http(args.lb)
        self.instances = [Http(u) for u in args.instances.split(",")]
        self.compose = ["docker", "compose", "-f", args.compose_file]

    def metrics(self):
        return [i.json("/metrics") for i in self.instances]

    def summed(self, field):
        return sum(m[field] for m in self.metrics())

    def docker(self, *cmd):
        subprocess.run(self.compose + list(cmd), check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def flush_redis(self):
        for n in (1, 2, 3):
            subprocess.run(self.compose + ["exec", "-T", f"redis-node-{n}", "redis-cli", "FLUSHDB"],
                           check=True, stdout=subprocess.DEVNULL)

    def wait_cache_mode(self, mode, timeout=60):
        deadline = time.time() + timeout
        while time.time() < deadline:
            modes = [i.json("/health")["cacheMode"] for i in self.instances]
            if all(m == mode for m in modes):
                return time.time()
            time.sleep(0.2)
        raise RuntimeError(f"cacheMode did not reach {mode}: {modes}")

    def load(self, path_fn, total, concurrency, method="GET", body_fn=None):
        """Fire `total` requests with `concurrency` workers; return list of (status, instance, ms, body)."""
        results = []
        lock = threading.Lock()

        def one(i):
            body = body_fn(i) if body_fn else None
            status, headers, payload, ms = self.lb.request(method, path_fn(i), body)
            with lock:
                results.append((status, headers.get("X-QueryFlow-Instance", "?"), ms, payload))

        with cf.ThreadPoolExecutor(max_workers=concurrency) as pool:
            list(pool.map(one, range(total)))
        return results


def hit_rate_delta(cluster, before):
    after = cluster.metrics()
    hits = sum(a["cacheHits"] - b["cacheHits"] for a, b in zip(after, before))
    misses = sum(a["cacheMisses"] - b["cacheMisses"] for a, b in zip(after, before))
    total = hits + misses
    return hits, misses, (round(100.0 * hits / total, 1) if total else 0.0)


# ---------------------------------------------------------------- phases

def phase_ratelimit(cluster, args):
    burst = args.ratelimit_burst
    statuses, retry_after = [], set()
    for _ in range(burst):
        status, headers, _, _ = cluster.lb.request("GET", "/suggest?q=ja")
        statuses.append(status)
        if status == 429:
            retry_after.add(headers.get("Retry-After"))
    m = cluster.metrics()
    return {
        "burst_requests": burst,
        "allowed_200": statuses.count(200),
        "rejected_429": statuses.count(429),
        "retry_after_header_values": sorted(x for x in retry_after if x),
        "rejected_per_instance_metric": {x["instanceId"]: x["rateLimitByEndpoint"]["suggest"]["rejected"] for x in m},
        "note": "Limits are per instance: with 2 instances one client can get up to 2x the configured burst.",
    }


def phase_traffic(cluster, args):
    cluster.flush_redis()
    before = cluster.metrics()
    t0 = time.perf_counter()
    res = cluster.load(lambda i: "/suggest?q=" + urllib.parse.quote(PREFIXES[i % len(PREFIXES)]),
                       args.requests, args.concurrency)
    elapsed = time.perf_counter() - t0
    hits, misses, rate = hit_rate_delta(cluster, before)
    per_instance = {}
    for status, inst, _, _ in res:
        per_instance[inst] = per_instance.get(inst, 0) + 1
    after = cluster.metrics()
    served_metric = {a["instanceId"]: a["suggestionRequests"] - b["suggestionRequests"] for a, b in zip(after, before)}
    lat = [ms for status, _, ms, _ in res if status == 200]
    return {
        "requests": len(res),
        "distinct_prefixes": len(PREFIXES),
        "concurrency": args.concurrency,
        "non_200": sum(1 for s, *_ in res if s != 200),
        "throughput_rps": round(len(res) / elapsed, 1),
        "served_by_header": dict(sorted(per_instance.items())),
        "served_by_instance_metrics": dict(sorted(served_metric.items())),
        "latency_client_side": summarize_latency(lat),
        "cache": {"hits": hits, "misses": misses, "hit_rate_pct": rate,
                  "note": "cold cache (FLUSHDB) at start; every miss is a first touch by some instance"},
    }


def phase_writes(cluster, args):
    stamp = str(int(time.time()))
    new_query = f"cluster bench {stamp}"
    queries = ["java", new_query]
    n = args.searches

    def count_of(q):
        status, _, payload, _ = cluster.lb.request("GET", "/trending/explain?query=" + urllib.parse.quote(q))
        return json.loads(payload)["count"] if status == 200 else 0

    before_counts = {q: count_of(q) for q in queries}
    before = cluster.metrics()
    res = cluster.load(lambda i: "/search", n, args.concurrency, method="POST",
                       body_fn=lambda i: {"query": queries[i % 2]})
    per_instance = {}
    for status, inst, _, _ in res:
        per_instance[inst] = per_instance.get(inst, 0) + 1
    non_200 = sum(1 for s, *_ in res if s != 200)

    # Wait until both instances have flushed everything they buffered.
    deadline = time.time() + args.flush_wait
    while time.time() < deadline:
        pending = sum(i.json("/batch/status")["pendingEvents"] for i in cluster.instances)
        if pending == 0:
            break
        time.sleep(0.5)
    after = cluster.metrics()
    db_write_txns = sum(a["databaseWrites"] - b["databaseWrites"] for a, b in zip(after, before))
    after_counts = {q: count_of(q) for q in queries}
    expected = {queries[0]: (n + 1) // 2, queries[1]: n // 2}
    deltas = {q: after_counts[q] - before_counts[q] for q in queries}
    return {
        "searches_submitted": n,
        "non_200": non_200,
        "submitted_per_instance": dict(sorted(per_instance.items())),
        "db_count_delta": deltas,
        "expected_delta": expected,
        "exact": deltas == expected,
        "db_write_transactions": db_write_txns,
        "write_reduction_pct": round(100.0 * (1 - db_write_txns / n), 2) if n else 0.0,
    }


def phase_outage(cluster, args):
    out = {}
    cluster.flush_redis()
    cluster.wait_cache_mode("FULL")
    # Baseline answers (cache warmed by this pass).
    baseline = {}
    for p in PREFIXES:
        _, _, payload, _ = cluster.lb.request("GET", "/suggest?q=" + urllib.parse.quote(p))
        baseline[p] = json.loads(payload)

    def checked_pass(label):
        before = cluster.metrics()
        wrong, non200, lat = 0, 0, []
        res = cluster.load(lambda i: "/suggest?q=" + urllib.parse.quote(PREFIXES[i % len(PREFIXES)]),
                           len(PREFIXES) * args.outage_rounds, args.concurrency)
        for status, _, ms, _ in res:
            if status != 200:
                non200 += 1
                continue
            lat.append(ms)
        # correctness: compare each prefix individually (order of results above is not stable)
        for p in PREFIXES:
            status, _, payload, _ = cluster.lb.request("GET", "/suggest?q=" + urllib.parse.quote(p))
            if status != 200:
                non200 += 1
            elif json.loads(payload) != baseline[p]:
                wrong += 1
        hits, misses, rate = hit_rate_delta(cluster, before)
        return {"label": label, "requests": len(res) + len(PREFIXES), "non_200": non200,
                "wrong_answers": wrong, "hit_rate_pct": rate, "hits": hits, "misses": misses,
                "latency_client_side": summarize_latency(lat)}

    out["healthy_warm"] = checked_pass("all nodes up, warm cache")

    cluster.docker("stop", "redis-node-2")
    out["one_node_down"] = checked_pass("redis-node-2 stopped")
    out["one_node_down"]["cacheMode"] = [i.json("/health")["cacheMode"] for i in cluster.instances]

    cluster.docker("stop", "redis-node-1", "redis-node-3")
    out["all_nodes_down"] = checked_pass("all 3 redis nodes stopped")
    out["all_nodes_down"]["cacheMode"] = [i.json("/health")["cacheMode"] for i in cluster.instances]

    t_start = time.time()
    cluster.docker("start", "redis-node-1", "redis-node-2", "redis-node-3")
    t_full = cluster.wait_cache_mode("FULL")
    out["recovery_seconds_until_cacheMode_FULL"] = round(t_full - t_start, 2)
    out["after_recovery_first_pass"] = checked_pass("redis restarted (empty), first pass refills")
    out["after_recovery_second_pass"] = checked_pass("redis restarted, second pass")
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lb", default="http://localhost:8080")
    ap.add_argument("--instances", default="http://localhost:8081,http://localhost:8082")
    ap.add_argument("--compose-file", default=os.path.join(os.path.dirname(__file__), "..", "docker-compose.cluster.yml"))
    ap.add_argument("--phases", default="traffic,writes,outage")
    ap.add_argument("--requests", type=int, default=5000)
    ap.add_argument("--concurrency", type=int, default=16)
    ap.add_argument("--searches", type=int, default=2000)
    ap.add_argument("--flush-wait", type=int, default=90)
    ap.add_argument("--outage-rounds", type=int, default=5)
    ap.add_argument("--ratelimit-burst", type=int, default=100)
    ap.add_argument("--json-out")
    args = ap.parse_args()

    cluster = Cluster(args)
    results = {"prefix_count": len(PREFIXES)}
    phases = {"ratelimit": phase_ratelimit, "traffic": phase_traffic, "writes": phase_writes, "outage": phase_outage}
    for name in args.phases.split(","):
        name = name.strip()
        print(f"== phase {name} ...", file=sys.stderr, flush=True)
        results[name] = phases[name](cluster, args)
        print(json.dumps({name: results[name]}, indent=2), flush=True)
    if args.json_out:
        with open(args.json_out, "w") as f:
            json.dump(results, f, indent=2)


if __name__ == "__main__":
    main()
