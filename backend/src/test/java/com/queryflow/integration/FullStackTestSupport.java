package com.queryflow.integration;

import com.queryflow.support.FakeRedisServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.List;

/**
 * Shared infrastructure for full-context tests: three fake Redis nodes on free
 * ports and an H2 database, so the whole application (filters, controllers,
 * services, JPA, Lettuce) runs inside `mvn test` with no external services.
 */
final class FullStackTestSupport {

    static final List<FakeRedisServer> REDIS = List.of(
            new FakeRedisServer(FakeRedisServer.freePort()),
            new FakeRedisServer(FakeRedisServer.freePort()),
            new FakeRedisServer(FakeRedisServer.freePort()));

    static {
        REDIS.forEach(FakeRedisServer::start);
    }

    private FullStackTestSupport() {
    }

    static void register(DynamicPropertyRegistry registry, String dbName) {
        for (int i = 0; i < 3; i++) {
            int port = REDIS.get(i).getPort();
            registry.add("redis.nodes.node" + (i + 1) + ".host", () -> "127.0.0.1");
            registry.add("redis.nodes.node" + (i + 1) + ".port", () -> port);
        }
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("queryflow.rate-limit.enabled", () -> "false");
        registry.add("queryflow.batch.flush-interval-seconds", () -> "3600");
        registry.add("queryflow.cache.redis.command-timeout-ms", () -> "300");
        registry.add("queryflow.cache.redis.connect-timeout-ms", () -> "300");
        registry.add("queryflow.cache.circuit-breaker.open-duration-ms", () -> "300");
        registry.add("queryflow.cache.circuit-breaker.probe-interval-ms", () -> "100");
    }

    static void startAll() {
        REDIS.forEach(FakeRedisServer::start);
    }

    static void stopAll() {
        REDIS.forEach(FakeRedisServer::stop);
    }
}
