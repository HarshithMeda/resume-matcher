package com.harshith.resumematcher;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ResumeMatcherApplicationTests extends ApiTestSupport {

    @Autowired
    DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    void healthCheckIsPublicAndShowsNoDetails() {
        Response res = send("GET", "/actuator/health", null, null, null);
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.json().get("status").asString()).isEqualTo("UP");
        assertThat(res.raw()).doesNotContain("components").doesNotContain("details").doesNotContain("PostgreSQL");
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        assertThat(send("GET", "/actuator/env", null, null, null).status()).isIn(401, 404);
        assertThat(send("GET", "/actuator/beans", null, null, null).status()).isIn(401, 404);
    }

    @Test
    void testsUseTheContainerNotTheLocalDatabase() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String url = c.getMetaData().getURL();
            assertThat(url).startsWith("jdbc:postgresql://").doesNotEndWith("/resumematcher");
            assertThat(c.getMetaData().getDatabaseProductVersion()).startsWith("17.");
        }
    }
}
