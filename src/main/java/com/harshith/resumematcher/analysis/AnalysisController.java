package com.harshith.resumematcher.analysis;

import com.harshith.resumematcher.auth.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @Operation(summary = "Match one of your resumes against a job description (50-15,000 characters)."
            + " The job description is not stored.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisResponse analyze(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AnalysisRequest request) {
        return service.analyze(CurrentUser.id(jwt), request);
    }

    @Operation(summary = "Your analyses, newest first (latest 100)")
    @GetMapping
    public List<AnalysisListItem> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @Operation(summary = "One of your analyses")
    @GetMapping("/{id}")
    public AnalysisResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return service.get(CurrentUser.id(jwt), id);
    }
}
