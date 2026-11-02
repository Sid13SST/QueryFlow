package com.queryflow.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Applies {@link RateLimiterService} to the public endpoints.
 *
 * Allowed responses carry X-RateLimit-Limit / X-RateLimit-Remaining. Rejected
 * requests get HTTP 429 with a Retry-After header (seconds) and a small JSON body.
 * CORS preflight (OPTIONS) requests are never limited.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(RateLimiterService rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !rateLimiter.isEnabled() || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<String> rule = rateLimiter.matchRule(path);
        if (rule.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        TokenBucket.Result result = rateLimiter.tryAcquire(rule.get(), clientIp);
        long limit = rateLimiter.getRule(rule.get()).getCapacity();
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (result.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfter = result.retryAfterSeconds();
        log.warn("RATE LIMITED rule={} client={} retryAfter={}s", rule.get(), clientIp, retryAfter);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        // Controllers use @CrossOrigin("*"); mirror that here so browsers can read the 429.
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                "Retry-After, X-RateLimit-Limit, X-RateLimit-Remaining");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"Too many requests\",\"endpoint\":\"" + rule.get()
                + "\",\"retryAfterSeconds\":" + retryAfter + "}");
    }

    String resolveClientIp(HttpServletRequest request) {
        if (properties.isTrustForwardedHeader()) {
            String header = request.getHeader(properties.getForwardedHeader());
            if (header != null && !header.isBlank()) {
                // Right-most entry = address seen by our own proxy; earlier entries are client-controlled.
                String[] parts = header.split(",");
                String candidate = parts[parts.length - 1].trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
