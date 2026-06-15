package com.queryflow.controller;

import com.queryflow.dto.TrendingExplainResponse;
import com.queryflow.dto.TrendingResponse;
import com.queryflow.service.TrendingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class TrendingController {

    @Autowired
    private TrendingService trendingService;

    @GetMapping("/trending")
    public ResponseEntity<List<TrendingResponse>> getTrending() {
        log.info("GET /trending requested");
        List<TrendingResponse> trending = trendingService.getTrending();
        return ResponseEntity.ok(trending);
    }

    @GetMapping("/trending/explain")
    public ResponseEntity<TrendingExplainResponse> explainTrending(@RequestParam("query") String query) {
        log.info("GET /trending/explain requested for query: '{}'", query);
        return trendingService.explainTrending(query)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
