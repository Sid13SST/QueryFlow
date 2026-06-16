package com.queryflow.service;

import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
            // Persist the batch transactionally
            persistBatch(snapshot);
            
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

    @Transactional
    public void persistBatch(Map<String, Integer> snapshot) {
        if (simulateDbOutage) {
            throw new RuntimeException("Simulated Database Outage");
        }
        Set<String> queriesToLookup = snapshot.keySet();
        metricsService.incrementDatabaseReads();
        List<SearchQuery> existing = searchQueryRepository.findByQueryIn(queriesToLookup);
        
        Map<String, SearchQuery> existingMap = existing.stream()
                .collect(Collectors.toMap(SearchQuery::getQuery, q -> q));
                
        List<SearchQuery> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
            String qStr = entry.getKey();
            long count = entry.getValue();

            SearchQuery sq = existingMap.get(qStr);
            if (sq != null) {
                sq.setCount(sq.getCount() + count);
                sq.setLastSearched(now);
                toSave.add(sq);
            } else {
                SearchQuery newSq = SearchQuery.builder()
                        .query(qStr)
                        .count(count)
                        .lastSearched(now)
                        .build();
                toSave.add(newSq);
            }
        }

        searchQueryRepository.saveAll(toSave);
        metricsService.incrementDatabaseWrites();
        log.info("Entries flushed: {}. Events flushed: {}.", snapshot.size(), snapshot.values().stream().mapToInt(Integer::intValue).sum());
    }

    public LocalDateTime getLastFlushTime() {
        return lastFlushTime;
    }
}
