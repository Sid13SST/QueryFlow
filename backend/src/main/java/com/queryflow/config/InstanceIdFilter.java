package com.queryflow.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps every response with the id of the backend instance that served it, so
 * load-balancer distribution can be observed from the outside (see
 * scripts/cluster_bench.py) and per-instance metrics can be attributed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-QueryFlow-Instance";

    private final String instanceId;

    public InstanceIdFilter(@Value("${queryflow.instance-id}") String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, instanceId);
        response.setHeader("Access-Control-Expose-Headers", HEADER);
        chain.doFilter(request, response);
    }
}
