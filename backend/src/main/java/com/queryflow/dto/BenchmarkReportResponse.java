package com.queryflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkReportResponse {
    private double cacheHitRate;
    private long estimatedDbReadsSaved;
    private double estimatedWriteReduction;
    private long batchFlushes;
}
