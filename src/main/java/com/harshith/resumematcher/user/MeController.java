package com.harshith.resumematcher.user;

import com.harshith.resumematcher.auth.CurrentUser;
import com.harshith.resumematcher.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @Operation(summary = "The currently logged-in user")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(CurrentUser.id(jwt))
                .map(UserResponse::from)
                // Valid signature but the account is gone (e.g. deleted): treat as not logged in.
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));
    }
}
