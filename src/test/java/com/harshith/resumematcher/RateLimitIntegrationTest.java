package com.harshith.resumematcher;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RateLimitIntegrationTest extends ApiTestSupport {

    private static final String JOB = "Requirements: Java, Spring Boot and PostgreSQL. Nice to have: Docker and Kafka.";

    private static long retryAfter(Response r) {
        return Long.parseLong(r.headers().firstValue("Retry-After").orElseThrow());
    }

    // ---- login: 5 failures per email ----

    @Test
    void loginLocksAnEmailAfterFiveFailures() {
        String ip = randomIp();
        String email = newEmail();
        register(email, PASSWORD, ip);

        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "wrong-password", ip).status()).isEqualTo(401);
        }
        Response blocked = login(email, "wrong-password", ip);
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.json().get("message").asString()).startsWith("Too many failed login attempts. Try again in");
        assertThat(retryAfter(blocked)).isBetween(1L, 15 * 60L);

        // Even the right password is refused while locked, so guessing can't continue.
        assertThat(login(email, PASSWORD, ip).status()).isEqualTo(429);
    }

    @Test
    void successfulLoginResetsTheEmailCount() {
        String ip = randomIp();
        String email = newEmail();
        register(email, PASSWORD, ip);

        for (int i = 0; i < 4; i++) {
            login(email, "wrong-password", ip);
        }
        assertThat(login(email, PASSWORD, ip).status()).isEqualTo(200);
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "wrong-password", ip).status()).isEqualTo(401);
        }
        assertThat(login(email, "wrong-password", ip).status()).isEqualTo(429);
    }

    // ---- login: 20 failures per IP ----

    @Test
    void loginLocksAnIpAfterTwentyFailures() {
        String ip = randomIp();
        for (int i = 0; i < 20; i++) {
            // Different (non-existent) emails each time, so only the IP limit applies.
            assertThat(login("nobody-" + i + "-" + ip + "@example.test", "wrong-password", ip).status()).isEqualTo(401);
        }
        Response blocked = login("someone-else@example.test", "wrong-password", ip);
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(retryAfter(blocked)).isBetween(1L, 15 * 60L);

        // Another IP is unaffected.
        assertThat(login("someone-else@example.test", "wrong-password", randomIp()).status()).isEqualTo(401);
    }

    // ---- register: 5 accounts per IP per hour ----

    @Test
    void registrationLimitedToFiveAccountsPerIp() {
        String ip = randomIp();
        String first = newEmail();
        assertThat(register(first, PASSWORD, ip).status()).isEqualTo(201);
        // Failed sign-ups (duplicate email) don't use up the allowance.
        for (int i = 0; i < 3; i++) {
            assertThat(register(first, PASSWORD, ip).status()).isEqualTo(409);
        }
        for (int i = 0; i < 4; i++) {
            assertThat(register(newEmail(), PASSWORD, ip).status()).isEqualTo(201);
        }

        Response blocked = register(newEmail(), PASSWORD, ip);
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.json().get("message").asString()).contains("Too many accounts");
        assertThat(retryAfter(blocked)).isBetween(1L, 3600L);

        // The X-Forwarded-For address is trusted here (request comes from 127.0.0.1, a listed proxy),
        // so a different client IP has its own allowance.
        assertThat(register(newEmail(), PASSWORD, randomIp()).status()).isEqualTo(201);
    }

    // ---- analyses: 30 per user per day ----

    @Test
    void analysesLimitedToThirtyPerDay() {
        String ip = randomIp();
        String email = newEmail();
        String token = registerAndLogin(email, ip);
        long resumeId = saveResume(email, "Java developer with Spring Boot and PostgreSQL experience.").getId();

        // A failed request (unknown resume) doesn't count.
        assertThat(analyse(token, 999_999_999L).status()).isEqualTo(404);
        for (int i = 0; i < 30; i++) {
            assertThat(analyse(token, resumeId).status()).isEqualTo(201);
        }
        Response blocked = analyse(token, resumeId);
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.json().get("message").asString())
                .startsWith("You've reached the limit of 30 analyses per day. It resets at 00:00 UTC, in ");
        assertThat(retryAfter(blocked)).isBetween(1L, 24 * 3600L);
    }

    private Response analyse(String token, long resumeId) {
        String body = JSON.writeValueAsString(Map.of("resumeId", resumeId, "jobDescription", JOB));
        return send("POST", "/api/analyses", body, token, null);
    }
}
