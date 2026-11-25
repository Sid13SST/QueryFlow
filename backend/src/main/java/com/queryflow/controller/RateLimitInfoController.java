package com.queryflow.controller;

import com.queryflow.ratelimit.ClientIpResolver;
import com.queryflow.ratelimit.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows the caller how the rate limiter sees them: the client address their bucket is keyed
 * on, where that address came from, and the configured limits. Used after a deployment to
 * check the proxy settings: two different visitors must get different {@code clientIp} values,
 * and a client-sent {@code X-Forwarded-For} must not change it.
 * Only the caller's own address is returned. Not itself rate limited.
 */
@RestController
@CrossOrigin(origins = "*")
public class RateLimitInfoController {

    private final ClientIpResolver clientIpResolver;
    private final RateLimitProperties properties;

    public RateLimitInfoController(ClientIpResolver clientIpResolver, RateLimitProperties properties) {
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
    }

    @GetMapping("/ratelimit/client")
    public Map<String, Object> client(HttpServletRequest request) {
        ClientIpResolver.Resolution resolution = clientIpResolver.resolveWithSource(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientIp", resolution.clientIp());
        body.put("source", resolution.source().name());
        body.put("enabled", properties.isEnabled());
        Map<String, Object> limits = new LinkedHashMap<>();
        properties.getEndpoints().forEach((name, rule) -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("paths", rule.getPaths());
            r.put("burst", rule.getCapacity());
            r.put("refillPerSecond", rule.getRefillPerSecond());
            limits.put(name, r);
        });
        body.put("limits", limits);
        return body;
    }
}
