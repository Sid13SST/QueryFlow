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
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(RateLimiterService rateLimiter, ClientIpResolver clientIpResolver) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
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

        String clientIp = clientIpResolver.resolve(request);
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
}
