package com.queryflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendingExplainResponse {
    private String query;
    private Long count;
    private Double hoursSinceLastSearch;
    private Double popularityScore;
    private Double recencyScore;
    private Double finalScore;
}
