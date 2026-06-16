package com.queryflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsLatencyResponse {
    private double suggestionAvgMs;
    private long suggestionMaxMs;
    private double trendingAvgMs;
    private long trendingMaxMs;
}
