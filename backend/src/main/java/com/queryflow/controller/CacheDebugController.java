package com.queryflow.controller;

import com.queryflow.cache.ResilientCacheClient;
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

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class CacheDebugController {

    @Autowired
    private ConsistentHashService consistentHashService;

    @Autowired
    private ResilientCacheClient cacheClient;

    @GetMapping("/cache/debug")
    public ResponseEntity<Map<String, Object>> debugRoute(@RequestParam("prefix") String prefix) {
        log.info("Incoming cache debug route check for prefix: {}", prefix);
        String cacheKey = "suggest:" + prefix.trim().toLowerCase();
        CacheNode routedNode = consistentHashService.route(cacheKey);

        Map<String, Object> response = new HashMap<>();
        response.put("prefix", prefix);
        
        if (routedNode != null) {
            response.put("node", routedNode.getNodeId());
            response.put("nodeState", cacheClient.getState(routedNode).name());
            response.put("cacheHit", cacheClient.hasKey(cacheKey));
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
            // -1 indicates the node is offline/unreachable (or its circuit is open)
            distribution.put(node.getNodeId(), cacheClient.countKeys(node, "suggest:*"));
        }
        
        return ResponseEntity.ok(distribution);
    }
}
