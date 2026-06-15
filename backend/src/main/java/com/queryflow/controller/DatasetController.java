package com.queryflow.controller;

import com.queryflow.dto.DatasetStatsResponse;
import com.queryflow.service.DatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@CrossOrigin(origins = "*")
public class DatasetController {

    @Autowired
    private DatasetService datasetService;

    @GetMapping("/dataset/stats")
    public ResponseEntity<DatasetStatsResponse> getStats() {
        long totalQueries = datasetService.getTotalQueriesCount();
        DatasetStatsResponse response = DatasetStatsResponse.builder()
                .totalQueries(totalQueries)
                .datasetLoaded(totalQueries > 0)
                .build();
        return ResponseEntity.ok(response);
    }
}
