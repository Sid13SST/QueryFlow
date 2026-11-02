package com.queryflow.cache;

import lombok.Builder;
import lombok.Value;

/** Snapshot of one Redis node's circuit, as reported by /health and /metrics. */
@Value
@Builder
public class CacheNodeStatus {
    String nodeId;
    String host;
    int port;
    CircuitBreaker.State state;
    long failures;
    long shortCircuited;
    long trips;
    long recoveries;
    int pendingInvalidations;
    boolean flushOnRecovery;
}
