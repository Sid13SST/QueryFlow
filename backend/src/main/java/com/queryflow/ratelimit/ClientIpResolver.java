package com.queryflow.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * Works out which client a request belongs to, for per-client rate limiting.
 *
 * <p>Order, when {@code trust-forwarded-header} is on:
 * <ol>
 *   <li>{@code client-ip-header} (e.g. {@code CF-Connecting-IP}), if configured and present.
 *       Use this only for a header the edge proxy always overwrites, so clients cannot
 *       choose it. On Render, Cloudflare sits in front of the service and sets
 *       {@code CF-Connecting-IP} on every request.</li>
 *   <li>{@code X-Forwarded-For}, counting {@code trusted-proxy-hops} entries from the right.
 *       Each trusted proxy appends the address it saw, so the N-th entry from the right is
 *       the address the outermost trusted proxy saw. Entries further left are written by the
 *       client and are ignored.</li>
 *   <li>The socket address.</li>
 * </ol>
 * With {@code trust-forwarded-header} off, the socket address is always used, so a client
 * that reaches the backend directly cannot pick its own bucket.
 */
public class ClientIpResolver {

    /** IPv4, IPv6 or IPv4-mapped IPv6 characters only; anything else is not used as a bucket key. */
    private static final Pattern IP_LIKE = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    public enum Source { CLIENT_IP_HEADER, FORWARDED_FOR, REMOTE_ADDR }

    public record Resolution(String clientIp, Source source) {
    }

    private final RateLimitProperties properties;

    public ClientIpResolver(RateLimitProperties properties) {
        if (properties.getTrustedProxyHops() < 1) {
            throw new IllegalStateException("queryflow.rate-limit.trusted-proxy-hops must be >= 1");
        }
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        return resolveWithSource(request).clientIp();
    }

    public Resolution resolveWithSource(HttpServletRequest request) {
        if (properties.isTrustForwardedHeader()) {
            String headerName = properties.getClientIpHeader();
            if (headerName != null && !headerName.isBlank()) {
                String value = clean(request.getHeader(headerName.trim()));
                if (value != null) {
                    return new Resolution(value, Source.CLIENT_IP_HEADER);
                }
            }

            String forwarded = request.getHeader(properties.getForwardedHeader());
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                int index = parts.length - properties.getTrustedProxyHops();
                // Fewer entries than trusted proxies means the request did not come through
                // all of them; fall back to the socket address rather than trust the header.
                if (index >= 0) {
                    String candidate = clean(parts[index]);
                    if (candidate != null) {
                        return new Resolution(candidate, Source.FORWARDED_FOR);
                    }
                }
            }
        }
        return new Resolution(request.getRemoteAddr(), Source.REMOTE_ADDR);
    }

    private static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        return IP_LIKE.matcher(v).matches() ? v : null;
    }
}
