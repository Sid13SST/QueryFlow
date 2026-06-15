package com.queryflow.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CacheNode {
    private String nodeId;
    private String host;
    private int port;

    @JsonIgnore
    private RedisTemplate<String, Object> redisTemplate;
}
