package com.queryflow.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PrefixGeneratorService {

    public List<String> generatePrefixes(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = query.trim().toLowerCase();
        List<String> prefixes = new ArrayList<>();
        for (int i = 1; i <= normalized.length(); i++) {
            prefixes.add(normalized.substring(0, i));
        }
        return prefixes;
    }
}
