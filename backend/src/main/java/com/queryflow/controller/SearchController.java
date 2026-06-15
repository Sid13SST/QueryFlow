package com.queryflow.controller;

import com.queryflow.dto.SearchRequest;
import com.queryflow.dto.SearchResponse;
import com.queryflow.service.SearchService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        log.info("Incoming search submission request received");
        searchService.recordSearch(request.getQuery());
        return ResponseEntity.ok(new SearchResponse(true, "Search recorded"));
    }
}
