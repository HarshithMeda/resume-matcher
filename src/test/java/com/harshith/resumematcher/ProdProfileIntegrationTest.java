package com.harshith.resumematcher;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** The production profile used by the Docker image (and so on Render). */
class ProdProfileIntegrationTest {

    @Nested
    @IntegrationTest
    @ActiveProfiles("prod")
    class Defaults extends ApiTestSupport {

        @Test
        void swaggerIsOffByDefault() {
            assertThat(send("GET", "/swagger-ui/index.html", null, null, null).status()).isEqualTo(404);
            assertThat(send("GET", "/v3/api-docs", null, null, null).status()).isEqualTo(404);
        }

        @Test
        void livenessHealthCheckIsPublicAndUp() {
            Response res = send("GET", "/actuator/health/liveness", null, null, null);
            assertThat(res.status()).isEqualTo(200);
            assertThat(res.json().get("status").asString()).isEqualTo("UP");
        }

        @Test
        void forwardedHeaderFromAnUntrustedConnectionIsIgnored() {
            // In prod only Render's network and Cloudflare are trusted; this test connects from 127.0.0.1,
            // so every request counts as 127.0.0.1 whatever X-Forwarded-For says.
            for (int i = 0; i < 5; i++) {
                assertThat(register(newEmail(), PASSWORD, randomIp()).status()).isEqualTo(201);
            }
            assertThat(register(newEmail(), PASSWORD, randomIp()).status()).isEqualTo(429);
        }
    }

    @Nested
    @IntegrationTest
    @ActiveProfiles("prod")
    @TestPropertySource(properties = "SWAGGER_ENABLED=true")
    class SwaggerSwitchedOn extends ApiTestSupport {

        @Test
        void swaggerCanBeTurnedOn() {
            assertThat(send("GET", "/swagger-ui/index.html", null, null, null).status()).isEqualTo(200);
            assertThat(send("GET", "/v3/api-docs", null, null, null).status()).isEqualTo(200);
        }
    }
}
