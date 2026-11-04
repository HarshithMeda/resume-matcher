package com.harshith.resumematcher;

import com.harshith.resumematcher.analysis.Analysis;
import com.harshith.resumematcher.analysis.AnalysisRepository;
import com.harshith.resumematcher.privacy.RetentionService;
import com.harshith.resumematcher.resume.Resume;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrivacyIntegrationTest extends ApiTestSupport {

    private static final String JOB = "Requirements: Java, Spring Boot and PostgreSQL. Nice to have: Docker and Kafka.";

    @Autowired
    AnalysisRepository analyses;
    @Autowired
    RetentionService retention;
    @Autowired
    JdbcTemplate jdbc;

    private Response analyse(String token, long resumeId) {
        String body = JSON.writeValueAsString(Map.of("resumeId", resumeId, "jobDescription", JOB));
        return send("POST", "/api/analyses", body, token, null);
    }

    private static String password(String pw) {
        return JSON.writeValueAsString(Map.of("password", pw));
    }

    // ---- DELETE /api/me ----

    @Test
    void deletingTheAccountNeedsThePasswordAndRemovesEverything() {
        String ip = randomIp();
        String email = newEmail();
        String token = registerAndLogin(email, ip);
        long userId = user(email).getId();
        long resumeId = saveResume(email, "Java and Spring Boot developer.").getId();
        saveResume(email, "Second resume text.");
        assertThat(analyse(token, resumeId).status()).isEqualTo(201);

        assertThat(send("DELETE", "/api/me", null, token, ip).status()).isEqualTo(400); // no password
        Response wrong = send("DELETE", "/api/me", password("wrong-password"), token, ip);
        assertThat(wrong.status()).isEqualTo(403);
        assertThat(wrong.json().get("message").asString()).isEqualTo("Incorrect password");
        assertThat(users.findById(userId)).isPresent();

        assertThat(send("DELETE", "/api/me", password(PASSWORD), token, ip).status()).isEqualTo(204);
        assertThat(users.findById(userId)).isEmpty();
        assertThat(resumes.countByUserId(userId)).isZero();
        assertThat(analyses.countByUserId(userId)).isZero();

        // The old token no longer works anywhere, and neither does logging in.
        assertThat(send("GET", "/api/me", null, token, ip).status()).isEqualTo(401);
        assertThat(send("GET", "/api/resumes", null, token, ip).status()).isEqualTo(401);
        assertThat(send("GET", "/api/analyses", null, token, ip).status()).isEqualTo(401);
        assertThat(login(email, PASSWORD, ip).status()).isEqualTo(401);
    }

    @Test
    void wrongPasswordsOnDeleteCountTowardTheLoginLimit() {
        String ip = randomIp();
        String email = newEmail();
        String token = registerAndLogin(email, ip);
        for (int i = 0; i < 5; i++) {
            assertThat(send("DELETE", "/api/me", password("guess-" + i), token, ip).status()).isEqualTo(403);
        }
        assertThat(send("DELETE", "/api/me", password(PASSWORD), token, ip).status()).isEqualTo(429);
    }

    // ---- GET /api/me/export ----

    @Test
    void exportContainsAllOfTheUsersDataAndNothingElse() {
        String ip = randomIp();
        String email = newEmail();
        String token = registerAndLogin(email, ip);
        long resumeId = saveResume(email, "Java and Spring Boot developer, exported text.").getId();
        assertThat(analyse(token, resumeId).status()).isEqualTo(201);

        String other = newEmail();
        registerAndLogin(other, ip);
        resumes.save(new Resume(user(other).getId(), "someone-else.pdf", "Someone else's resume."));

        Response res = send("GET", "/api/me/export", null, token, ip);
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Disposition").orElseThrow())
                .contains("attachment").contains("resume-matcher-export.json");
        assertThat(res.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");

        JsonNode body = res.json();
        assertThat(body.get("account").get("email").asString()).isEqualTo(email);
        assertThat(body.get("resumes")).hasSize(1);
        assertThat(body.get("resumes").get(0).get("extractedText").asString()).contains("exported text");
        assertThat(body.get("analyses")).hasSize(1);
        assertThat(body.get("analyses").get(0).get("resumeId").asLong()).isEqualTo(resumeId);
        assertThat(res.raw()).doesNotContain("passwordHash").doesNotContain("$2a$").doesNotContain("someone-else.pdf");
    }

    // ---- retention ----

    @Test
    void retentionDeletesOnlyDataOlderThanTheLimit() {
        String email = newEmail();
        register(email, PASSWORD, randomIp());
        long userId = user(email).getId();

        Resume old = saveResume(email, "Old resume.");
        Resume recent = saveResume(email, "Recent resume.");
        Resume borderline = saveResume(email, "89 days old.");
        Analysis onOld = analyses.save(new Analysis(userId, old.getId(), 50, "{}"));
        Analysis oldOnRecent = analyses.save(new Analysis(userId, recent.getId(), 60, "{}"));
        Analysis recentOnRecent = analyses.save(new Analysis(userId, recent.getId(), 70, "{}"));
        ageDays("resumes", old.getId(), 91);
        ageDays("resumes", borderline.getId(), 89);
        ageDays("analyses", oldOnRecent.getId(), 91);

        RetentionService.Purged purged = retention.purgeExpired();

        assertThat(purged.resumes()).isGreaterThanOrEqualTo(1);
        assertThat(purged.analyses()).isGreaterThanOrEqualTo(2);
        assertThat(resumes.findById(old.getId())).isEmpty();
        assertThat(analyses.findById(onOld.getId())).isEmpty();          // went with its old resume
        assertThat(analyses.findById(oldOnRecent.getId())).isEmpty();    // old itself
        assertThat(resumes.findById(recent.getId())).isPresent();
        assertThat(resumes.findById(borderline.getId())).isPresent();
        assertThat(analyses.findById(recentOnRecent.getId())).isPresent();
        assertThat(users.findById(userId)).isPresent();                  // accounts are kept
    }

    @Test
    void retentionPeriodIsConfigurableAndMustBePositive() {
        assertThat(retention.retentionDays()).isEqualTo(90);
        assertThatThrownBy(() -> new RetentionService(resumes, analyses, Clock.systemUTC(), 0))
                .hasMessageContaining("at least 1");
    }

    private void ageDays(String table, long id, int days) {
        // Table name comes from the test itself, not from input.
        jdbc.update("update " + table + " set created_at = now() - make_interval(days => ?) where id = ?", days, id);
    }

    // ---- privacy note ----

    @Test
    void privacyNoteIsPublic() {
        Response res = send("GET", "/api/privacy", null, null, null);
        assertThat(res.status()).isEqualTo(200);
        JsonNode body = res.json();
        assertThat(body.get("retentionDays").asInt()).isEqualTo(90);
        assertThat(body.get("whatWeNeverStore").toString()).contains("Original PDF files").contains("Job descriptions");
        assertThat(body.get("retention").asString()).contains("90 days");
    }
}
