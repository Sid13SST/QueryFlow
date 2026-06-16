package com.queryflow.controller;

import com.queryflow.dto.BenchmarkReportResponse;
import com.queryflow.service.BenchmarkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class BenchmarkController {

    @Autowired
    private BenchmarkService benchmarkService;

    @GetMapping("/benchmark/report")
    public ResponseEntity<BenchmarkReportResponse> getReport() {
        log.info("GET /benchmark/report requested");
        BenchmarkReportResponse response = benchmarkService.generateReport();
        return ResponseEntity.ok(response);
    }
}
