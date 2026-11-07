package com.harshith.resumematcher;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Here 127.0.0.1 is NOT a trusted proxy, so X-Forwarded-For must be ignored: a client can't dodge
 * the per-IP limits by sending a fake header.
 */
@IntegrationTest
@TestPropertySource(properties = "app.client-ip.trusted-proxies=10.255.255.1/32")
class UntrustedProxyIntegrationTest extends ApiTestSupport {

    @Test
    void forwardedHeaderFromAnUntrustedSourceIsIgnored() {
        for (int i = 0; i < 5; i++) {
            assertThat(register(newEmail(), PASSWORD, randomIp()).status()).isEqualTo(201);
        }
        // Every request above really came from 127.0.0.1, whatever the header said.
        assertThat(register(newEmail(), PASSWORD, randomIp()).status()).isEqualTo(429);
    }
}
