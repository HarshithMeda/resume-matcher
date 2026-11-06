package com.harshith.resumematcher.config;

import com.harshith.resumematcher.error.ApiError;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.harshith.resumematcher.user.UserRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32; // HS256 needs a key of at least 256 bits

    private static final String[] PUBLIC_PATHS = {
            "/api/auth/**",
            "/api/privacy",
            "/actuator/health", "/actuator/health/**",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/error",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json) throws Exception {
        AuthenticationEntryPoint unauthorized = jsonEntryPoint(json);
        AccessDeniedHandler forbidden = jsonAccessDenied(json);

        http
                // Stateless JWT API: no cookies or sessions, so CSRF protection isn't needed.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                // Built-in support: reads "Authorization: Bearer <token>" and checks it with jwtDecoder().
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> {})
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtSigningKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            // Never include the secret itself in the message.
            throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, @Value("${app.jwt.issuer}") String issuer, UserRepository users) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256) // only accept HS256 (rejects "alg": "none" etc.)
                .build();
        // Checks expiry (exp/nbf), that we issued the token, and that the account still exists
        // (so a deleted account's token stops working immediately, not after it expires).
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), userStillExists(users)));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> userStillExists(UserRepository users) {
        OAuth2Error gone = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Account no longer exists", null);
        return jwt -> jwt.getClaims().get("uid") instanceof Number uid && users.existsById(uid.longValue())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(gone);
    }

    /** 401 as JSON. The standard WWW-Authenticate header is still set by Spring's entry point. */
    private static AuthenticationEntryPoint jsonEntryPoint(JsonMapper json) {
        BearerTokenAuthenticationEntryPoint headers = new BearerTokenAuthenticationEntryPoint();
        return (request, response, ex) -> {
            headers.commence(request, response, ex);
            String message = ex instanceof OAuth2AuthenticationException
                    ? "Invalid or expired token"
                    : "Authentication required";
            write(json, request, response, HttpStatus.UNAUTHORIZED, message);
        };
    }

    private static AccessDeniedHandler jsonAccessDenied(JsonMapper json) {
        BearerTokenAccessDeniedHandler headers = new BearerTokenAccessDeniedHandler();
        return (request, response, ex) -> {
            headers.handle(request, response, ex);
            write(json, request, response, HttpStatus.FORBIDDEN, "Access denied");
        };
    }

    private static void write(JsonMapper json, HttpServletRequest request, HttpServletResponse response,
                              HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        json.writeValue(response.getOutputStream(), body);
    }
}
