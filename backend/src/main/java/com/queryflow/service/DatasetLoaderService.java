package com.queryflow.service;

import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class DatasetLoaderService {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${queryflow.dataset.path:data/search_queries.csv}")
    private String datasetPath;

    @Transactional
    public void loadDataset() {
        log.info("Dataset loading started");
        
        int processedCount = 0;
        int insertedCount = 0;
        int duplicateCount = 0;
        int skippedCount = 0;

        try {
            Resource resource = resourceLoader.getResource(datasetPath.startsWith("classpath:") ? datasetPath : "classpath:" + datasetPath);
            if (!resource.exists()) {
                log.error("Dataset file not found at path: {}", datasetPath);
                return;
            }

            // Fetch existing queries to prevent duplicates
            List<String> dbQueries = searchQueryRepository.findAllQueries();
            Set<String> existing = new HashSet<>(dbQueries);
            Set<String> processedInBatch = new HashSet<>();

            List<SearchQuery> batch = new ArrayList<>();
            int batchSize = 1000;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                // Skip header line
                String header = br.readLine();
                
                while ((line = br.readLine()) != null) {
                    processedCount++;
                    String[] parts = line.split(",", -1);
                    if (parts.length < 2) {
                        log.warn("Skipping invalid row (invalid columns): {}", line);
                        skippedCount++;
                        continue;
                    }

                    String query = parts[0].trim();
                    String countStr = parts[1].trim();

                    if (query.isEmpty()) {
                        log.warn("Skipping invalid row (empty query): {}", line);
                        skippedCount++;
                        continue;
                    }

                    long count;
                    try {
                        count = Long.parseLong(countStr);
                    } catch (NumberFormatException e) {
                        log.warn("Skipping invalid row (invalid count '{}'): {}", countStr, line);
                        skippedCount++;
                        continue;
                    }

                    if (existing.contains(query) || processedInBatch.contains(query)) {
                        duplicateCount++;
                        continue;
                    }

                    processedInBatch.add(query);
                    batch.add(SearchQuery.builder()
                            .query(query)
                            .count(count)
                            .build());

                    if (batch.size() >= batchSize) {
                        searchQueryRepository.saveAll(batch);
                        insertedCount += batch.size();
                        batch.clear();
                    }
                }

                if (!batch.isEmpty()) {
                    searchQueryRepository.saveAll(batch);
                    insertedCount += batch.size();
                    batch.clear();
                }
            }

            log.info("Records processed: {}", processedCount);
            log.info("Records inserted: {}", insertedCount);
            log.info("Duplicates skipped: {}", duplicateCount);
            if (skippedCount > 0) {
                log.info("Invalid records skipped: {}", skippedCount);
            }
            log.info("Dataset loading completed");

        } catch (Exception e) {
            log.error("Failed to load dataset: {}", e.getMessage(), e);
            // Catch error, but do not crash the startup
        }
    }
}
