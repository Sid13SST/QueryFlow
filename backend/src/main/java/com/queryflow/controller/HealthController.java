package com.queryflow.controller;

import lombok.extern.slf4j.Slf4j;
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        log.info("Incoming health check request received.");
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "QueryFlow");
        return ResponseEntity.ok(response);
    }
}
