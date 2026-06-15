package com.queryflow.service;

import com.queryflow.repository.SearchQueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasetService {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    public long getTotalQueriesCount() {
        return searchQueryRepository.count();
    }
}
