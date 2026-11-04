package com.harshith.resumematcher.auth;

import com.harshith.resumematcher.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** Reads the logged-in user's id from the "uid" claim of a validated JWT. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static long id(Jwt jwt) {
        if (jwt == null || !(jwt.getClaims().get("uid") instanceof Number uid)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
        return uid.longValue();
    }
}
