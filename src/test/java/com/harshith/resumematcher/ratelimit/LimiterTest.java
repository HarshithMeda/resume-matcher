package com.harshith.resumematcher.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));

    // ---- sliding window (login, register) ----

    @Test
    void allowsUpToTheLimitThenBlocksWithTimeUntilASlotFrees() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, Duration.ofMinutes(15), clock);
        assertThat(limiter.tryAcquire("k")).isEmpty();
        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.tryAcquire("k")).isEmpty();
        assertThat(limiter.tryAcquire("k")).isEmpty();
        // The first event (10:00) leaves the window at 10:15; it's now 10:05.
        assertThat(limiter.tryAcquire("k")).contains(Duration.ofMinutes(10));
    }

    @Test
    void windowSlides() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(2, Duration.ofMinutes(15), clock);
        limiter.tryAcquire("k");
        limiter.tryAcquire("k");
        assertThat(limiter.tryAcquire("k")).isPresent();
        clock.advance(Duration.ofMinutes(15));
        assertThat(limiter.tryAcquire("k")).isEmpty();
    }

    @Test
    void keysAreIndependent() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(1, Duration.ofHours(1), clock);
        assertThat(limiter.tryAcquire("a")).isEmpty();
        assertThat(limiter.tryAcquire("a")).isPresent();
        assertThat(limiter.tryAcquire("b")).isEmpty();
    }

    @Test
    void releaseAndReset() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(2, Duration.ofHours(1), clock);
        limiter.tryAcquire("k");
        limiter.tryAcquire("k");
        limiter.release("k");
        assertThat(limiter.tryAcquire("k")).isEmpty();   // released slot is free again
        assertThat(limiter.tryAcquire("k")).isPresent();
        limiter.reset("k");
        assertThat(limiter.tryAcquire("k")).isEmpty();   // reset clears everything
    }

    @Test
    void cleanUpDropsExpiredKeys() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(5, Duration.ofMinutes(15), clock);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        clock.advance(Duration.ofMinutes(16));
        limiter.tryAcquire("c");
        limiter.cleanUp();
        assertThat(limiter.trackedKeys()).isEqualTo(1);
    }

    // ---- daily (analyses) ----

    @Test
    void dailyLimitBlocksAndResetsAtMidnightUtc() {
        DailyLimiter limiter = new DailyLimiter(30, clock);
        for (int i = 0; i < 30; i++) {
            assertThat(limiter.tryAcquire(7L)).isEmpty();
        }
        assertThat(limiter.tryAcquire(7L)).contains(Instant.parse("2026-11-05T00:00:00Z"));
        assertThat(limiter.tryAcquire(8L)).isEmpty(); // other users unaffected

        clock.advance(Duration.ofHours(14)); // 00:00 UTC next day
        assertThat(limiter.tryAcquire(7L)).isEmpty();
    }

    @Test
    void dailyReleaseGivesBackOneUse() {
        DailyLimiter limiter = new DailyLimiter(1, clock);
        limiter.tryAcquire(1L);
        limiter.release(1L);
        assertThat(limiter.tryAcquire(1L)).isEmpty();
    }

    // ---- messages ----

    @Test
    void humanReadableWaitTimesAndRetryAfter() {
        assertThat(RateLimitedException.describe(Duration.ofSeconds(1))).isEqualTo("1 second");
        assertThat(RateLimitedException.describe(Duration.ofSeconds(45))).isEqualTo("45 seconds");
        assertThat(RateLimitedException.describe(Duration.ofSeconds(61))).isEqualTo("2 minutes");
        assertThat(RateLimitedException.describe(Duration.ofMinutes(60))).isEqualTo("1 hour");
        assertThat(RateLimitedException.describe(Duration.ofMinutes(185))).isEqualTo("3 hours 5 minutes");
        assertThat(new RateLimitedException("x", Duration.ofMillis(1500)).retryAfterSeconds()).isEqualTo(2);
        assertThat(new RateLimitedException("x", Duration.ZERO).retryAfterSeconds()).isEqualTo(1);
    }
}
