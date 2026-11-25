package com.queryflow.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    private static RateLimitProperties props(boolean trust, int hops, String clientIpHeader) {
        RateLimitProperties p = new RateLimitProperties();
        p.setTrustForwardedHeader(trust);
        p.setTrustedProxyHops(hops);
        p.setClientIpHeader(clientIpHeader);
        return p;
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/suggest");
        r.setRemoteAddr(remoteAddr);
        return r;
    }

    @Test
    void ignoresHeadersUnlessTrusted() {
        MockHttpServletRequest r = request("10.0.0.5");
        r.addHeader("X-Forwarded-For", "203.0.113.9");
        r.addHeader("CF-Connecting-IP", "203.0.113.9");

        ClientIpResolver.Resolution res = new ClientIpResolver(props(false, 1, "CF-Connecting-IP")).resolveWithSource(r);

        assertThat(res.clientIp()).isEqualTo("10.0.0.5");
        assertThat(res.source()).isEqualTo(ClientIpResolver.Source.REMOTE_ADDR);
    }

    /** Shape seen on Render: Cloudflare appends the client, Render's proxy appends the Cloudflare edge. */
    @Test
    void renderBehindCloudflareUsesConnectingIpHeader() {
        MockHttpServletRequest r = request("10.25.239.2");
        r.addHeader("X-Forwarded-For", "6.6.6.6, 198.51.100.7, 172.70.1.1");
        r.addHeader("CF-Connecting-IP", "198.51.100.7");

        ClientIpResolver.Resolution res = new ClientIpResolver(props(true, 1, "CF-Connecting-IP")).resolveWithSource(r);

        assertThat(res.clientIp()).isEqualTo("198.51.100.7");
        assertThat(res.source()).isEqualTo(ClientIpResolver.Source.CLIENT_IP_HEADER);
    }

    @Test
    void hopCountPicksTheEntryTheOutermostTrustedProxySaw() {
        MockHttpServletRequest r = request("10.25.239.2");
        r.addHeader("X-Forwarded-For", "6.6.6.6, 198.51.100.7, 172.70.1.1");

        assertThat(new ClientIpResolver(props(true, 1, "")).resolve(r)).isEqualTo("172.70.1.1");
        assertThat(new ClientIpResolver(props(true, 2, "")).resolve(r)).isEqualTo("198.51.100.7");
    }

    @Test
    void shorterChainThanTrustedHopsFallsBackToSocketAddress() {
        MockHttpServletRequest r = request("10.0.0.5");
        r.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(new ClientIpResolver(props(true, 2, "")).resolve(r)).isEqualTo("10.0.0.5");
    }

    @Test
    void missingOrGarbageClientIpHeaderFallsBackToForwardedFor() {
        MockHttpServletRequest missing = request("10.0.0.5");
        missing.addHeader("X-Forwarded-For", "203.0.113.9");
        MockHttpServletRequest garbage = request("10.0.0.5");
        garbage.addHeader("X-Forwarded-For", "203.0.113.9");
        garbage.addHeader("CF-Connecting-IP", "<script>");

        ClientIpResolver resolver = new ClientIpResolver(props(true, 1, "CF-Connecting-IP"));

        assertThat(resolver.resolveWithSource(missing).source()).isEqualTo(ClientIpResolver.Source.FORWARDED_FOR);
        assertThat(resolver.resolve(missing)).isEqualTo("203.0.113.9");
        assertThat(resolver.resolve(garbage)).isEqualTo("203.0.113.9");
    }

    @Test
    void acceptsIpv6() {
        MockHttpServletRequest r = request("10.0.0.5");
        r.addHeader("CF-Connecting-IP", "2001:db8::1");

        assertThat(new ClientIpResolver(props(true, 1, "CF-Connecting-IP")).resolve(r)).isEqualTo("2001:db8::1");
    }

    @Test
    void rejectsNonPositiveHopCount() {
        assertThatThrownBy(() -> new ClientIpResolver(props(true, 0, "")))
                .isInstanceOf(IllegalStateException.class);
    }
}
