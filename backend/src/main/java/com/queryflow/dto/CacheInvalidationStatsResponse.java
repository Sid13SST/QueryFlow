package com.queryflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CacheInvalidationStatsResponse {
    private long invalidations;
    private LocalDateTime lastInvalidationTime;
}
