package com.queryflow.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SearchService {

    @Autowired
    private SearchBufferService searchBufferService;

    public void recordSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be blank");
        }

        String normalizedQuery = query.trim();
        log.info("Search received (buffered): '{}'", normalizedQuery);

        searchBufferService.increment(normalizedQuery);
    }
}
