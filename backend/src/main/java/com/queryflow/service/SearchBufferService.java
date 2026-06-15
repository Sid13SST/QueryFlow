package com.queryflow.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class SearchBufferService {

    private final ConcurrentHashMap<String, Integer> buffer = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong pendingEventsCount = new AtomicLong(0);

    public void increment(String query) {
        lock.readLock().lock();
        try {
            buffer.merge(query, 1, Integer::sum);
            pendingEventsCount.incrementAndGet();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, Integer> flush() {
        lock.writeLock().lock();
        try {
            Map<String, Integer> snapshot = new HashMap<>(buffer);
            buffer.clear();
            pendingEventsCount.set(0);
            return snapshot;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getPendingEntries() {
        lock.readLock().lock();
        try {
            return buffer.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public long getPendingEvents() {
        return pendingEventsCount.get();
    }

    public void rollback(Map<String, Integer> snapshot) {
        lock.writeLock().lock();
        try {
            for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
                buffer.merge(entry.getKey(), entry.getValue(), Integer::sum);
                pendingEventsCount.addAndGet(entry.getValue());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
