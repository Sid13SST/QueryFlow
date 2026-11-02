package com.queryflow.config;

import com.queryflow.cache.CacheResilienceProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.Delay;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(CacheResilienceProperties.class)
public class RedisConfig {

    /**
     * Per-command and connect timeouts. Redis answers in well under a millisecond
     * on a LAN, so these only bound how long a dead or hung node can stall one
     * request before the circuit breaker takes over.
     */
    @Value("${queryflow.cache.redis.command-timeout-ms:500}")
    private long commandTimeoutMs;

    @Value("${queryflow.cache.redis.connect-timeout-ms:500}")
    private long connectTimeoutMs;

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

    /**
     * Shared Lettuce resources. Lettuce reconnects automatically after a node comes
     * back, but its default backoff grows to 30 s; capping it at 2 s bounds how long
     * a recovered node stays unused.
     */
    public static ClientResources createClientResources() {
        return DefaultClientResources.builder()
                .reconnectDelay(Delay.exponential(Duration.ofMillis(50), Duration.ofSeconds(2), 2, TimeUnit.MILLISECONDS))
                .build();
    }

    public static LettuceConnectionFactory createConnectionFactory(String host, int port, long connectTimeoutMs,
                                                                   long commandTimeoutMs, ClientResources resources) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        // Fail fast while disconnected instead of buffering commands until reconnect.
        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(commandTimeoutMs))
                .clientOptions(clientOptions)
                .clientResources(resources)
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    public static RedisTemplate<String, Object> createRedisTemplate(LettuceConnectionFactory connectionFactory) {
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

    @Bean(destroyMethod = "shutdown")
    public ClientResources lettuceClientResources() {
        return createClientResources();
    }

    @Bean
    @Primary
    public LettuceConnectionFactory redisNode1ConnectionFactory() {
        return createConnectionFactory(node1Host, node1Port, connectTimeoutMs, commandTimeoutMs, lettuceClientResources());
    }

    @Bean
    public LettuceConnectionFactory redisNode2ConnectionFactory() {
        return createConnectionFactory(node2Host, node2Port, connectTimeoutMs, commandTimeoutMs, lettuceClientResources());
    }

    @Bean
    public LettuceConnectionFactory redisNode3ConnectionFactory() {
        return createConnectionFactory(node3Host, node3Port, connectTimeoutMs, commandTimeoutMs, lettuceClientResources());
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
