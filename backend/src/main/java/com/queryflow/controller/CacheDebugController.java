package com.queryflow.controller;

import com.queryflow.dto.CacheNode;
import com.queryflow.service.ConsistentHashService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class CacheDebugController {

    @Autowired
    private ConsistentHashService consistentHashService;

    @GetMapping("/cache/debug")
    public ResponseEntity<Map<String, Object>> debugRoute(@RequestParam("prefix") String prefix) {
        log.info("Incoming cache debug route check for prefix: {}", prefix);
        String cacheKey = "suggest:" + prefix.trim().toLowerCase();
        CacheNode routedNode = consistentHashService.route(cacheKey);

        Map<String, Object> response = new HashMap<>();
        response.put("prefix", prefix);
        
        if (routedNode != null) {
            response.put("node", routedNode.getNodeId());
            boolean cacheHit = false;
            try {
                cacheHit = Boolean.TRUE.equals(routedNode.getRedisTemplate().hasKey(cacheKey));
            } catch (Exception e) {
                log.warn("Error checking key availability on node {}: {}", routedNode.getNodeId(), e.getMessage());
            }
            response.put("cacheHit", cacheHit);
        } else {
            response.put("node", "none");
            response.put("cacheHit", false);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/cache/ring")
    public ResponseEntity<Map<String, Object>> inspectRing() {
        log.info("Incoming request to inspect consistent hash ring");
        Map<String, Object> response = new HashMap<>();
        response.put("totalNodes", consistentHashService.getNodes().size());
        response.put("virtualNodes", consistentHashService.getRingSize());
        
        // Include detailed nodes metadata for demonstration
        response.put("nodes", consistentHashService.getNodes());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/cache/distribution")
    public ResponseEntity<Map<String, Long>> getDistribution() {
        log.info("Incoming request to compute key distribution across nodes");
        Map<String, Long> distribution = new HashMap<>();
        
        for (CacheNode node : consistentHashService.getNodes()) {
            try {
                Set<String> keys = node.getRedisTemplate().keys("suggest:*");
                distribution.put(node.getNodeId(), keys != null ? (long) keys.size() : 0L);
            } catch (Exception e) {
                log.warn("Failed to get keys count for node {}: {}", node.getNodeId(), e.getMessage());
                distribution.put(node.getNodeId(), -1L); // -1 indicates node is offline/unreachable
            }
        }
        
        return ResponseEntity.ok(distribution);
    }
}
