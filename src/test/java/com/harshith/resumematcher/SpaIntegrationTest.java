package com.harshith.resumematcher;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Serving the React app. Uses a stand-in index.html from src/test/resources/static. */
@IntegrationTest
class SpaIntegrationTest extends ApiTestSupport {

    @ParameterizedTest
    @ValueSource(strings = {"/", "/login", "/register", "/privacy", "/resumes", "/analyze", "/history", "/account", "/results/12"})
    void appPagesServeIndexHtmlWithoutLoggingIn(String path) {
        Response res = send("GET", path, null, null, null);
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.raw()).contains("test spa shell");
        assertThat(res.headers().firstValue("Content-Security-Policy").orElseThrow())
                .contains("default-src 'self'").contains("script-src 'self'").contains("frame-ancestors 'none'");
    }

    @Test
    void swaggerUiKeepsWorkingWithoutTheStrictPolicy() {
        Response res = send("GET", "/swagger-ui/index.html", null, null, null);
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Security-Policy")).isEmpty();
    }

    @Test
    void staticFilesArePublicForGetOnly() {
        assertThat(send("GET", "/index.html", null, null, null).status()).isEqualTo(200);
        assertThat(send("POST", "/index.html", "{}", null, null).status()).isEqualTo(401);
        assertThat(send("PUT", "/assets/app.js", "{}", null, null).status()).isEqualTo(401);
        assertThat(send("DELETE", "/favicon.svg", null, null, null).status()).isEqualTo(401);
        assertThat(send("POST", "/resumes", "{}", null, null).status()).isEqualTo(401);
    }

    @Test
    void apiStillNeedsAToken() {
        assertThat(send("GET", "/api/resumes", null, null, null).status()).isEqualTo(401);
    }
}
