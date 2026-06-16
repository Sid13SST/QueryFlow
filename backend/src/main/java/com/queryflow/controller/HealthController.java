package com.queryflow.controller;

import com.queryflow.repository.SearchQueryRepository;
import com.queryflow.service.ConsistentHashService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.info("Incoming health check request received.");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        
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
        
        return ResponseEntity.ok(response);
    }
}
