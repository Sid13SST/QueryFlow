package com.queryflow.service;

import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class SearchService {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Transactional
    public void recordSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be blank");
        }

        String normalizedQuery = query.trim();
        log.info("Search received: '{}'", normalizedQuery);

        Optional<SearchQuery> existing = searchQueryRepository.findByQuery(normalizedQuery);
        if (existing.isPresent()) {
            SearchQuery searchQuery = existing.get();
            searchQuery.setCount(searchQuery.getCount() + 1);
            searchQuery.setLastSearched(LocalDateTime.now());
            searchQueryRepository.save(searchQuery);
            log.info("Existing query updated: '{}' (new count: {})", normalizedQuery, searchQuery.getCount());
        } else {
            SearchQuery newQuery = SearchQuery.builder()
                    .query(normalizedQuery)
                    .count(1L)
                    .lastSearched(LocalDateTime.now())
                    .build();
            searchQueryRepository.save(newQuery);
            log.info("New query inserted: '{}'", normalizedQuery);
        }
    }
}
