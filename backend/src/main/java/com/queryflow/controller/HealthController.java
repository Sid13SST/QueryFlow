package com.queryflow.controller;

import com.queryflow.cache.ResilientCacheClient;
import com.queryflow.repository.SearchQueryRepository;
import com.queryflow.service.ConsistentHashService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.HashMap;
import java.util.Map;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class HealthController {

    @Autowired
    private SearchQueryRepository searchQueryRepository;

    @Autowired
    private ConsistentHashService consistentHashService;

    @Autowired
    private ResilientCacheClient cacheClient;

    @Value("${queryflow.instance-id:local}")
    private String instanceId;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.info("Incoming health check request received.");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("instanceId", instanceId);
        
        try {
            searchQueryRepository.count();
            response.put("database", "CONNECTED");
        } catch (Exception e) {
            response.put("database", "DOWN");
        }

        int nodeCount = consistentHashService.getNodes().size();
        response.put("redisNodes", nodeCount);
        
        int ringSize = consistentHashService.getRingSize();
        response.put("cacheRing", ringSize > 0 ? "ACTIVE" : "INACTIVE");

        // FULL / DEGRADED / OFFLINE. Suggestions keep working in every mode (PostgreSQL fallback).
        response.put("cacheMode", cacheClient.getCacheMode());
        response.put("cacheNodes", cacheClient.getNodeStatuses());
        
        return ResponseEntity.ok(response);
    }
}
