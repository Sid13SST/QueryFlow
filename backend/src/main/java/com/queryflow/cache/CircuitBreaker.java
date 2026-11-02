package com.queryflow.cache;

import java.util.function.LongSupplier;

/**
 * Minimal circuit breaker guarding one Redis node.
 *
 * <pre>
 * CLOSED --(failureThreshold consecutive failures)--> OPEN
 * OPEN   --(openDurationMs elapsed, next call)-------> HALF_OPEN (exactly one probe call allowed)
 * HALF_OPEN --probe succeeds--> CLOSED
 * HALF_OPEN --probe fails-----> OPEN (timer restarts)
 * </pre>
 *
 * While OPEN, callers are told immediately not to touch the node, so a dead
 * node costs nothing per request instead of a connect/command timeout.
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** What the caller is allowed to do right now. */
    public enum Permit { NORMAL, PROBE, DENIED }

    private final int failureThreshold;
    private final long openDurationMs;
    private final LongSupplier clockMs;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAtMs;
    private boolean probeInFlight;

    private long totalFailures;
    private long shortCircuited;
    private long trips;
    private long recoveries;

    public CircuitBreaker(int failureThreshold, long openDurationMs, LongSupplier clockMs) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
        this.clockMs = clockMs;
    }

    public synchronized Permit tryAcquire() {
        switch (state) {
            case CLOSED:
                return Permit.NORMAL;
            case OPEN:
                if (clockMs.getAsLong() - openedAtMs >= openDurationMs) {
                    state = State.HALF_OPEN;
                    probeInFlight = true;
                    return Permit.PROBE;
                }
                shortCircuited++;
                return Permit.DENIED;
            case HALF_OPEN:
            default:
                if (!probeInFlight) {
                    probeInFlight = true;
                    return Permit.PROBE;
                }
                shortCircuited++;
                return Permit.DENIED;
        }
    }

    public synchronized void onSuccess() {
        consecutiveFailures = 0;
        probeInFlight = false;
        if (state != State.CLOSED) {
            state = State.CLOSED;
            recoveries++;
        }
    }

    public synchronized void onFailure() {
        totalFailures++;
        probeInFlight = false;
        if (state == State.HALF_OPEN) {
            trip();
            return;
        }
        consecutiveFailures++;
        if (state == State.CLOSED && consecutiveFailures >= failureThreshold) {
            trip();
        }
    }

    /** True when the breaker is OPEN and its wait has elapsed, i.e. a probe would be allowed. */
    public synchronized boolean isProbeDue() {
        return state == State.OPEN && clockMs.getAsLong() - openedAtMs >= openDurationMs;
    }

    private void trip() {
        state = State.OPEN;
        openedAtMs = clockMs.getAsLong();
        consecutiveFailures = 0;
        trips++;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized long getTotalFailures() {
        return totalFailures;
    }

    public synchronized long getShortCircuited() {
        return shortCircuited;
    }

    public synchronized long getTrips() {
        return trips;
    }

    public synchronized long getRecoveries() {
        return recoveries;
    }
}
