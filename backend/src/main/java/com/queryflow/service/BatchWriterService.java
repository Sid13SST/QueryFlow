package com.queryflow.service;

import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class BatchWriterService {

    @Autowired
    private SearchBufferService searchBufferService;

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private CacheInvalidationService cacheInvalidationService;

    @Autowired
    private MetricsService metricsService;

    private LocalDateTime lastFlushTime;

    /** Real transactions when running in Spring; plain execution in Spring-free unit tests. */
    private TransactionOperations transactionOperations = TransactionOperations.withoutTransaction();

    @Autowired(required = false)
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionOperations = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${queryflow.batch.flush-interval-seconds:30}", timeUnit = TimeUnit.SECONDS)
    public void scheduleFlush() {
        log.info("Batch flush started");
        long startTime = System.currentTimeMillis();
        
        Map<String, Integer> snapshot = searchBufferService.flush();
        if (snapshot.isEmpty()) {
            log.info("Batch flush completed. Buffer was empty.");
            lastFlushTime = LocalDateTime.now();
            return;
        }

        int entriesCount = snapshot.size();
        long eventsCount = snapshot.values().stream().mapToLong(Integer::longValue).sum();

        try {
            // Persist the batch in ONE transaction. persistBatch is called on `this`, so a
            // @Transactional annotation on it would be bypassed (no proxy); use an explicit template.
            transactionOperations.executeWithoutResult(status -> persistBatch(snapshot));
            
            // Invalidate affected cache entries ONLY after successful DB update
            try {
                cacheInvalidationService.invalidateQueries(snapshot.keySet());
                cacheInvalidationService.invalidateTrending();
            } catch (Exception e) {
                log.warn("Cache invalidation encountered an error but processing will continue: {}", e.getMessage());
            }
            
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordBatchFlush(duration);
            log.info("Batch flush completed. Entries={} Events={} Duration={}ms", entriesCount, eventsCount, duration);
            lastFlushTime = LocalDateTime.now();
            
        } catch (Exception e) {
            log.error("Failed to persist batch. Restoring buffer snapshot.", e);
            searchBufferService.rollback(snapshot);
        }
    }

    private boolean simulateDbOutage = false;

    public void setSimulateDbOutage(boolean simulateDbOutage) {
        this.simulateDbOutage = simulateDbOutage;
    }

    public boolean isSimulateDbOutage() {
        return simulateDbOutage;
    }

    /**
     * Applies one aggregated batch. Must run inside a single transaction (see
     * {@link #scheduleFlush()}) so that a failure part-way leaves the database
     * untouched and the snapshot can be safely re-buffered without double counting.
     *
     * Multi-instance safety: existing rows are incremented with an atomic
     * {@code count = count + delta} UPDATE instead of load-modify-save, and keys
     * are processed in sorted order so concurrent flushes from different instances
     * lock rows in the same order (no deadlocks between them). If two instances
     * insert the same brand-new query at once, the unique constraint rejects one
     * transaction; that instance rolls back and re-applies on its next flush, at
     * which point the row exists and the UPDATE path is taken.
     */
    public void persistBatch(Map<String, Integer> snapshot) {
        if (simulateDbOutage) {
            throw new RuntimeException("Simulated Database Outage");
        }
        LocalDateTime now = LocalDateTime.now();
        List<SearchQuery> toInsert = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : new TreeMap<>(snapshot).entrySet()) {
            int updated = searchQueryRepository.incrementCount(entry.getKey(), entry.getValue(), now);
            if (updated == 0) {
                toInsert.add(SearchQuery.builder()
                        .query(entry.getKey())
                        .count(entry.getValue().longValue())
                        .lastSearched(now)
                        .build());
            }
        }

        if (!toInsert.isEmpty()) {
            searchQueryRepository.saveAllAndFlush(toInsert);
        }
        metricsService.incrementDatabaseWrites();
        log.info("Entries flushed: {} ({} updated, {} inserted). Events flushed: {}.", snapshot.size(),
                snapshot.size() - toInsert.size(), toInsert.size(),
                snapshot.values().stream().mapToInt(Integer::intValue).sum());
    }

    public LocalDateTime getLastFlushTime() {
        return lastFlushTime;
    }
}
