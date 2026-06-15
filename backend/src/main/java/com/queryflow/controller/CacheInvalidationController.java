package com.queryflow.controller;

import com.queryflow.dto.CacheInvalidationStatsResponse;
import com.queryflow.service.CacheInvalidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class CacheInvalidationController {

    @Autowired
    private CacheInvalidationService cacheInvalidationService;

    @GetMapping("/cache/invalidation/stats")
    public ResponseEntity<CacheInvalidationStatsResponse> getInvalidationStats() {
        log.info("GET /cache/invalidation/stats requested");
        CacheInvalidationStatsResponse response = CacheInvalidationStatsResponse.builder()
                .invalidations(cacheInvalidationService.getInvalidationCount())
                .lastInvalidationTime(cacheInvalidationService.getLastInvalidationTime())
                .build();
        return ResponseEntity.ok(response);
    }
}
