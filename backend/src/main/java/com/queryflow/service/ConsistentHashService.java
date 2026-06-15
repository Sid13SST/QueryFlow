package com.queryflow.service;

import com.queryflow.dto.CacheNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

@Service
@Slf4j
public class ConsistentHashService {

    @Value("${redis.nodes.node1.host}")
    private String node1Host;
    
    @Value("${redis.nodes.node1.port}")
    private int node1Port;

    @Value("${redis.nodes.node2.host}")
    private String node2Host;
    
    @Value("${redis.nodes.node2.port}")
    private int node2Port;

    @Value("${redis.nodes.node3.host}")
    private String node3Host;
    
    @Value("${redis.nodes.node3.port}")
    private int node3Port;

    @Autowired
    @Qualifier("redisNode1Template")
    private RedisTemplate<String, Object> node1Template;

    @Autowired
    @Qualifier("redisNode2Template")
    private RedisTemplate<String, Object> node2Template;

    @Autowired
    @Qualifier("redisNode3Template")
    private RedisTemplate<String, Object> node3Template;

    private final TreeMap<Long, CacheNode> ring = new TreeMap<>();
    private final List<CacheNode> nodes = new ArrayList<>();

    @PostConstruct
    public void init() {
        CacheNode node1 = CacheNode.builder()
                .nodeId("redis-node-1")
                .host(node1Host)
                .port(node1Port)
                .redisTemplate(node1Template)
                .build();

        CacheNode node2 = CacheNode.builder()
                .nodeId("redis-node-2")
                .host(node2Host)
                .port(node2Port)
                .redisTemplate(node2Template)
                .build();

        CacheNode node3 = CacheNode.builder()
                .nodeId("redis-node-3")
                .host(node3Host)
                .port(node3Port)
                .redisTemplate(node3Template)
                .build();

        nodes.add(node1);
        nodes.add(node2);
        nodes.add(node3);

        // Register 100 virtual nodes for each physical cache node
        for (CacheNode node : nodes) {
            for (int i = 0; i < 100; i++) {
                String virtualNodeKey = node.getNodeId() + "-V" + i;
                long virtualHash = hash(virtualNodeKey);
                ring.put(virtualHash, node);
            }
        }
        
        log.info("Consistent Hash Ring initialized with {} physical nodes and {} virtual nodes", 
                nodes.size(), ring.size());
    }

    /**
     * Deterministic hash function using MD5.
     * MD5 is selected because:
     * 1. It offers highly uniform distribution of hashed keys, reducing potential imbalances on the ring.
     * 2. It is natively supported by java.security.MessageDigest, avoiding dependencies on external libraries.
     */
    public long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(key.getBytes(StandardCharsets.UTF_8));
            // Read first 8 bytes of the 16-byte MD5 digest as a long
            long result = 0;
            for (int i = 0; i < 8; i++) {
                result = (result << 8) | (bytes[i] & 0xFF);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 digest algorithm not found", e);
        }
    }

    /**
     * Routes a cache key to its next clockwise node in the consistent hash ring.
     */
    public CacheNode route(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        long h = hash(key);
        SortedMap<Long, CacheNode> tailMap = ring.tailMap(h);
        long nodeHash = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();
        return ring.get(nodeHash);
    }

    public List<CacheNode> getNodes() {
        return nodes;
    }

    public int getRingSize() {
        return ring.size();
    }
}
