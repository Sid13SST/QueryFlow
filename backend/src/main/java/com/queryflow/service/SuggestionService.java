package com.queryflow.service;

import com.queryflow.dto.SuggestionResponse;
import com.queryflow.entity.SearchQuery;
import com.queryflow.repository.SearchQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SuggestionService {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    public List<SuggestionResponse> getSuggestions(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Query prefix cannot be empty");
        }

        String normalizedPrefix = prefix.trim();
        log.info("Prefix received: '{}'", normalizedPrefix);

        List<SearchQuery> queries = searchQueryRepository.findTop10ByQueryStartingWithIgnoreCaseOrderByCountDesc(normalizedPrefix);
        
        log.info("Number of results returned: {}", queries.size());

        return queries.stream()
                .map(q -> new SuggestionResponse(q.getQuery(), q.getCount()))
                .collect(Collectors.toList());
    }
}
