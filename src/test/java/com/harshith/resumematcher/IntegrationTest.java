package com.harshith.resumematcher;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Starts the full app on a random port, backed by a PostgreSQL 17 container.
 * Needs Docker, but no local.properties: the JWT secret below is a test-only dummy value.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt.secret=test-only-dummy-jwt-secret-never-used-outside-tests-0123456789")
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
