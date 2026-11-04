package com.harshith.resumematcher.privacy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirements // public: shown before sign-up
public class PrivacyController {

    private final PrivacyNotice notice;

    public PrivacyController(RetentionService retention) {
        this.notice = PrivacyNotice.forRetention(retention.retentionDays());
    }

    @Operation(summary = "What we store, what we never store, and how long we keep it")
    @GetMapping("/api/privacy")
    public PrivacyNotice privacy() {
        return notice;
    }
}
