package com.harshith.resumematcher.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes, so longer passwords are rejected.
        @NotBlank @Size(min = 8, max = 72) String password) {
}
