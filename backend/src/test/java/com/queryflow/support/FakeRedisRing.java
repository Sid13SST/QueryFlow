package com.queryflow.support;

import com.queryflow.config.RedisConfig;
import com.queryflow.dto.CacheNode;
import com.queryflow.service.ConsistentHashService;
import io.lettuce.core.resource.ClientResources;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Three {@link FakeRedisServer}s wired into the production {@link ConsistentHashService}
 * through the production Lettuce/RedisTemplate configuration, without Spring.
 */
public class FakeRedisRing implements AutoCloseable {

    public final List<FakeRedisServer> servers = new ArrayList<>();
    public final ConsistentHashService ring = new ConsistentHashService();
    private final List<LettuceConnectionFactory> factories = new ArrayList<>();
    private final ClientResources resources = RedisConfig.createClientResources();

    public FakeRedisRing() {
        for (int i = 1; i <= 3; i++) {
            FakeRedisServer server = new FakeRedisServer(FakeRedisServer.freePort());
            server.start();
            servers.add(server);
            LettuceConnectionFactory f = RedisConfig.createConnectionFactory(
                    "127.0.0.1", server.getPort(), 300, 300, resources);
            factories.add(f);
            ReflectionTestUtils.setField(ring, "node" + i + "Host", "127.0.0.1");
            ReflectionTestUtils.setField(ring, "node" + i + "Port", server.getPort());
            ReflectionTestUtils.setField(ring, "node" + i + "Template", RedisConfig.createRedisTemplate(f));
        }
        ring.init();
    }

    /** The fake server backing the node a key routes to. */
    public FakeRedisServer serverFor(String key) {
        return serverFor(ring.route(key));
    }

    public FakeRedisServer serverFor(CacheNode node) {
        return servers.get(ring.getNodes().indexOf(node));
    }

    @Override
    public void close() {
        factories.forEach(LettuceConnectionFactory::destroy);
        servers.forEach(FakeRedisServer::stop);
        resources.shutdown();
    }
}
