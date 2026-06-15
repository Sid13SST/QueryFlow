package com.queryflow.controller;

import com.queryflow.dto.SuggestionResponse;
import com.queryflow.service.SuggestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@Slf4j
@CrossOrigin(origins = "*")
public class SuggestionController {

    @Autowired
    private SuggestionService suggestionService;

    @GetMapping("/suggest")
    public ResponseEntity<List<SuggestionResponse>> suggest(@RequestParam(value = "q", required = false) String query) {
        log.info("Incoming suggestion request");
        List<SuggestionResponse> suggestions = suggestionService.getSuggestions(query);
        return ResponseEntity.ok(suggestions);
    }
}
