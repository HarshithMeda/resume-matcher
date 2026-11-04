package com.harshith.resumematcher.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/** The app's rate limits, configured under app.rate-limit.* in application.properties. */
@Component
public class RateLimits {

    private final SlidingWindowLimiter loginFailuresPerEmail;
    private final SlidingWindowLimiter loginFailuresPerIp;
    private final SlidingWindowLimiter registrationsPerIp;
    private final DailyLimiter analysesPerUser;

    public RateLimits(
            Clock clock,
            @Value("${app.rate-limit.login.max-failures-per-email}") int loginPerEmail,
            @Value("${app.rate-limit.login.max-failures-per-ip}") int loginPerIp,
            @Value("${app.rate-limit.login.window}") Duration loginWindow,
            @Value("${app.rate-limit.register.max-per-ip}") int registerPerIp,
            @Value("${app.rate-limit.register.window}") Duration registerWindow,
            @Value("${app.rate-limit.analyses.max-per-user-per-day}") int analysesPerDay) {
        this.loginFailuresPerEmail = new SlidingWindowLimiter(loginPerEmail, loginWindow, clock);
        this.loginFailuresPerIp = new SlidingWindowLimiter(loginPerIp, loginWindow, clock);
        this.registrationsPerIp = new SlidingWindowLimiter(registerPerIp, registerWindow, clock);
        this.analysesPerUser = new DailyLimiter(analysesPerDay, clock);
    }

    public SlidingWindowLimiter loginFailuresPerEmail() {
        return loginFailuresPerEmail;
    }

    public SlidingWindowLimiter loginFailuresPerIp() {
        return loginFailuresPerIp;
    }

    public SlidingWindowLimiter registrationsPerIp() {
        return registrationsPerIp;
    }

    public DailyLimiter analysesPerUser() {
        return analysesPerUser;
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M")
    public void cleanUp() {
        loginFailuresPerEmail.cleanUp();
        loginFailuresPerIp.cleanUp();
        registrationsPerIp.cleanUp();
        analysesPerUser.cleanUp();
    }
}
