package com.harshith.resumematcher.ratelimit;

import com.harshith.resumematcher.error.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Duration;

/** A 429 response; the handler also sends a Retry-After header. */
public class RateLimitedException extends ApiException {

    private final Duration retryAfter;

    public RateLimitedException(String message, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfter = retryAfter;
    }

    /** Whole seconds, rounded up, at least 1 (the Retry-After header format). */
    public long retryAfterSeconds() {
        long seconds = (retryAfter.toMillis() + 999) / 1000;
        return Math.max(1, seconds);
    }

    /** "45 seconds", "12 minutes", "3 hours 5 minutes" (rounded up). */
    public static String describe(Duration d) {
        long seconds = Math.max(1, (d.toMillis() + 999) / 1000);
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        String h = hours + (hours == 1 ? " hour" : " hours");
        return rest == 0 ? h : h + " " + rest + (rest == 1 ? " minute" : " minutes");
    }
}
