package com.queryflow.controller;

import com.queryflow.dto.BatchStatusResponse;
import com.queryflow.service.SearchBufferService;
import com.queryflow.service.BatchWriterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
public class BatchController {

    @Autowired
    private SearchBufferService searchBufferService;

    @Autowired
    private BatchWriterService batchWriterService;

    @GetMapping("/batch/status")
    public ResponseEntity<BatchStatusResponse> getBatchStatus() {
        BatchStatusResponse response = BatchStatusResponse.builder()
                .pendingEntries(searchBufferService.getPendingEntries())
                .pendingEvents(searchBufferService.getPendingEvents())
                .lastFlushTime(batchWriterService.getLastFlushTime())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/batch/toggle-outage")
    public ResponseEntity<String> toggleOutage() {
        boolean current = batchWriterService.isSimulateDbOutage();
        batchWriterService.setSimulateDbOutage(!current);
        return ResponseEntity.ok("Simulated DB Outage is now " + (!current ? "ENABLED" : "DISABLED"));
    }
}
