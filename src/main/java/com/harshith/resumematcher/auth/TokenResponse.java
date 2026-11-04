package com.harshith.resumematcher.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
