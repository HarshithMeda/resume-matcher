package com.harshith.resumematcher.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    // Like production: Render's private range plus two Cloudflare ranges, at most 2 hops.
    private final ClientIpResolver render = new ClientIpResolver(
            List.of("10.0.0.0/8", "172.64.0.0/13", "104.24.0.0/14", "2606:4700::/32"), 2);
    // Like local/tests: only this machine, no extra hops.
    private final ClientIpResolver local = new ClientIpResolver(List.of("127.0.0.1/32", "::1/128"), 0);

    private static final String RENDER_PROXY = "10.226.90.65";  // the connection comes from here
    private static final String CF_EDGE = "172.71.195.123";

    @Test
    void renderChainGivesTheRealClient() {
        assertThat(render.resolve(RENDER_PROXY, "81.97.145.24, " + CF_EDGE + ", 10.226.90.66")).isEqualTo("81.97.145.24");
    }

    @Test
    void clientCannotFakeTheirIpWithTheirOwnHeader() {
        // The client sent "X-Forwarded-For: 1.2.3.4"; Cloudflare and Render append after it.
        assertThat(render.resolve(RENDER_PROXY, "1.2.3.4, 81.97.145.24, " + CF_EDGE + ", 10.226.90.66"))
                .isEqualTo("81.97.145.24");
        assertThat(render.resolve(RENDER_PROXY, "9.9.9.9, 8.8.8.8, 7.7.7.7, 81.97.145.24, " + CF_EDGE + ", 10.0.0.9"))
                .isEqualTo("81.97.145.24");
    }

    @Test
    void connectingThroughACloudflareAddressStillCannotFakeTheIp() {
        // A client using Cloudflare WARP (104.28.x.x is inside a Cloudflare range) adds a fake entry.
        // We stop after Render's and Cloudflare's hops, so the WARP address is the client, not the fake.
        assertThat(render.resolve(RENDER_PROXY, "1.2.3.4, 104.28.5.6, " + CF_EDGE + ", 10.0.0.9"))
                .isEqualTo("104.28.5.6");
    }

    @Test
    void shorterChainsStillWork() {
        // If Render only appended its own hop, the first untrusted entry is still the client.
        assertThat(render.resolve(RENDER_PROXY, "81.97.145.24, 10.0.0.9")).isEqualTo("81.97.145.24");
        assertThat(render.resolve(RENDER_PROXY, "81.97.145.24")).isEqualTo("81.97.145.24");
    }

    @Test
    void headerIsIgnoredWhenTheConnectionIsNotFromATrustedProxy() {
        assertThat(render.resolve("81.97.145.24", "1.2.3.4")).isEqualTo("81.97.145.24");
        assertThat(local.resolve("192.168.1.20", "1.2.3.4")).isEqualTo("192.168.1.20");
    }

    @Test
    void missingOrMalformedHeadersFallBackToTheConnection() {
        assertThat(render.resolve(RENDER_PROXY, null)).isEqualTo(RENDER_PROXY);
        assertThat(render.resolve(RENDER_PROXY, " ")).isEqualTo(RENDER_PROXY);
        assertThat(render.resolve(RENDER_PROXY, "evil.example.com, " + CF_EDGE + ", 10.0.0.9"))
                .isEqualTo(RENDER_PROXY);
        assertThat(render.resolve(RENDER_PROXY, "999.1.1.1, " + CF_EDGE + ", 10.0.0.9")).isEqualTo(RENDER_PROXY);
        assertThat(render.resolve(null, null)).isEqualTo("unknown");
    }

    @Test
    void ipv6Works() {
        assertThat(render.resolve(RENDER_PROXY, "2001:db8::1, 2606:4700::10, 10.0.0.9")).isEqualTo("2001:db8::1");
    }

    @Test
    void localProxyPassesTheClientThrough() {
        assertThat(local.resolve("127.0.0.1", "203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(local.resolve("0:0:0:0:0:0:0:1", "203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(local.resolve("127.0.0.1", null)).isEqualTo("127.0.0.1");
    }
}
