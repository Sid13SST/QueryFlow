package com.queryflow.controller;

import com.queryflow.dto.CacheStatsResponse;
import com.queryflow.service.CacheMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class CacheStatsController {

    @Autowired
    private CacheMetricsService cacheMetricsService;

    @GetMapping("/cache/stats")
    public ResponseEntity<CacheStatsResponse> getCacheStats() {
        log.info("Incoming cache stats request");
        CacheStatsResponse stats = cacheMetricsService.getStats();
        return ResponseEntity.ok(stats);
    }
}
