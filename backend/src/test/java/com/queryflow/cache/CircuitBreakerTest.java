package com.queryflow.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static com.queryflow.cache.CircuitBreaker.Permit.*;
import static com.queryflow.cache.CircuitBreaker.State.*;
import static org.junit.jupiter.api.Assertions.*;

class CircuitBreakerTest {

    private final AtomicLong clock = new AtomicLong();
    private final CircuitBreaker breaker = new CircuitBreaker(3, 1000, clock::get);

    @Test
    void staysClosedBelowThresholdAndSuccessResetsCount() {
        breaker.onFailure();
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onFailure();
        breaker.onFailure();
        assertEquals(CLOSED, breaker.getState());
        assertEquals(NORMAL, breaker.tryAcquire());
    }

    @Test
    void opensAfterConsecutiveFailuresAndShortCircuits() {
        for (int i = 0; i < 3; i++) {
            breaker.onFailure();
        }
        assertEquals(OPEN, breaker.getState());
        assertEquals(DENIED, breaker.tryAcquire());
        assertEquals(DENIED, breaker.tryAcquire());
        assertEquals(2, breaker.getShortCircuited());
        assertEquals(1, breaker.getTrips());
    }

    @Test
    void allowsExactlyOneProbeAfterOpenDuration() {
        for (int i = 0; i < 3; i++) {
            breaker.onFailure();
        }
        clock.addAndGet(999);
        assertEquals(DENIED, breaker.tryAcquire());
        clock.addAndGet(1);
        assertTrue(breaker.isProbeDue());
        assertEquals(PROBE, breaker.tryAcquire());
        assertEquals(HALF_OPEN, breaker.getState());
        assertEquals(DENIED, breaker.tryAcquire(), "concurrent callers wait for the probe");
    }

    @Test
    void successfulProbeClosesTheCircuit() {
        for (int i = 0; i < 3; i++) {
            breaker.onFailure();
        }
        clock.addAndGet(1000);
        breaker.tryAcquire();
        breaker.onSuccess();
        assertEquals(CLOSED, breaker.getState());
        assertEquals(1, breaker.getRecoveries());
        assertEquals(NORMAL, breaker.tryAcquire());
    }

    @Test
    void failedProbeReopensAndRestartsTimer() {
        for (int i = 0; i < 3; i++) {
            breaker.onFailure();
        }
        clock.addAndGet(1000);
        breaker.tryAcquire();
        breaker.onFailure();
        assertEquals(OPEN, breaker.getState());
        assertEquals(2, breaker.getTrips());
        clock.addAndGet(500);
        assertEquals(DENIED, breaker.tryAcquire());
        clock.addAndGet(500);
        assertEquals(PROBE, breaker.tryAcquire());
    }
}
