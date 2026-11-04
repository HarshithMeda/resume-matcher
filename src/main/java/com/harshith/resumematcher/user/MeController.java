package com.harshith.resumematcher.user;

import com.harshith.resumematcher.auth.ClientIp;
import com.harshith.resumematcher.auth.CurrentUser;
import com.harshith.resumematcher.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserRepository users;
    private final AccountService accounts;

    public MeController(UserRepository users, AccountService accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    @Operation(summary = "The currently logged-in user")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(CurrentUser.id(jwt))
                .map(UserResponse::from)
                // Valid signature but the account is gone (e.g. deleted): treat as not logged in.
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));
    }

    @Operation(summary = "Delete your account and all its resumes and analyses. Requires your password.")
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest request,
                       HttpServletRequest http) {
        accounts.deleteAccount(CurrentUser.id(jwt), request.password(), ClientIp.of(http));
    }

    @Operation(summary = "Download all your data as JSON")
    @GetMapping("/me/export")
    public ResponseEntity<AccountExport> export(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("resume-matcher-export.json").build().toString())
                .cacheControl(CacheControl.noStore()) // personal data: don't keep copies in caches
                .body(accounts.export(CurrentUser.id(jwt)));
    }
}
