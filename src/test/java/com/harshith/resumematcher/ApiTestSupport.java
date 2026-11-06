package com.harshith.resumematcher;

import com.harshith.resumematcher.resume.Resume;
import com.harshith.resumematcher.resume.ResumeRepository;
import com.harshith.resumematcher.user.User;
import com.harshith.resumematcher.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Helpers for tests that call the real running app over HTTP (so Tomcat's proxy handling runs too).
 * Runs against a PostgreSQL container (see {@link IntegrationTest}); accounts a test creates are
 * deleted afterwards so tests sharing a container stay independent.
 * Subclasses are annotated with {@link IntegrationTest}.
 */
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correct-horse-battery";
    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected ResumeRepository resumes;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<String> createdEmails = new ArrayList<>();

    protected record Response(int status, String raw, HttpHeaders headers) {
        public JsonNode json() {
            return raw.isEmpty() ? null : JSON.readTree(raw);
        }
    }

    @AfterEach
    void deleteTestAccounts() {
        createdEmails.forEach(e -> users.findByEmail(e).ifPresent(users::delete));
    }

    /** A fresh email for this test; its account is deleted after the test. */
    protected String newEmail() {
        String email = "it-" + UUID.randomUUID() + "@example.test";
        createdEmails.add(email);
        return email;
    }

    /** A random documentation-range IP, so tests don't share rate-limit counters. */
    protected static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "203.0." + r.nextInt(0, 256) + "." + r.nextInt(1, 255);
    }

    /** Sends a request from 127.0.0.1, claiming {@code ip} in X-Forwarded-For (if not null). */
    protected Response send(String method, String path, String jsonBody, String token, String ip) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .method(method, jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody));
        if (jsonBody != null) {
            b.header("Content-Type", "application/json");
        }
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (ip != null) {
            b.header("X-Forwarded-For", ip);
        }
        try {
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(res.statusCode(), res.body(), res.headers());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    protected Response register(String email, String password, String ip) {
        return send("POST", "/api/auth/register", credentials(email, password), null, ip);
    }

    protected Response login(String email, String password, String ip) {
        return send("POST", "/api/auth/login", credentials(email, password), null, ip);
    }

    protected String registerAndLogin(String email, String ip) {
        register(email, PASSWORD, ip);
        return login(email, PASSWORD, ip).json().get("accessToken").asString();
    }

    protected User user(String email) {
        return users.findByEmail(email).orElseThrow();
    }

    /** Saves a resume directly (skips PDF parsing, which has its own tests). */
    protected Resume saveResume(String email, String text) {
        return resumes.save(new Resume(user(email).getId(), "test-resume.pdf", text));
    }

    protected static String credentials(String email, String password) {
        return JSON.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }
}
