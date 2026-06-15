package com.queryflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

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

    private LettuceConnectionFactory createConnectionFactory(String host, int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        
        io.lettuce.core.SocketOptions socketOptions = io.lettuce.core.SocketOptions.builder()
                .connectTimeout(java.time.Duration.ofMillis(1000))
                .build();
                
        io.lettuce.core.ClientOptions clientOptions = io.lettuce.core.ClientOptions.builder()
                .socketOptions(socketOptions)
                .disconnectedBehavior(io.lettuce.core.ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build();
                
        org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration clientConfig = 
                org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                        .commandTimeout(java.time.Duration.ofMillis(1000))
                        .clientOptions(clientOptions)
                        .build();
                        
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    @Primary
    public LettuceConnectionFactory redisNode1ConnectionFactory() {
        return createConnectionFactory(node1Host, node1Port);
    }

    @Bean
    public LettuceConnectionFactory redisNode2ConnectionFactory() {
        return createConnectionFactory(node2Host, node2Port);
    }

    @Bean
    public LettuceConnectionFactory redisNode3ConnectionFactory() {
        return createConnectionFactory(node3Host, node3Port);
    }

    private RedisTemplate<String, Object> createRedisTemplate(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        
        // Use JSON serializer for values
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisNode1Template")
    @Primary
    public RedisTemplate<String, Object> redisNode1Template() {
        return createRedisTemplate(redisNode1ConnectionFactory());
    }

    @Bean(name = "redisNode2Template")
    public RedisTemplate<String, Object> redisNode2Template() {
        return createRedisTemplate(redisNode2ConnectionFactory());
    }

    @Bean(name = "redisNode3Template")
    public RedisTemplate<String, Object> redisNode3Template() {
        return createRedisTemplate(redisNode3ConnectionFactory());
    }
}

