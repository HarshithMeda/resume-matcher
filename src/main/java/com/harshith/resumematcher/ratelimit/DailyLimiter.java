package com.harshith.resumematcher.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "At most N per key per calendar day (UTC)", kept in memory. Every count resets at 00:00 UTC,
 * which makes the reset time easy to tell the user.
 */
public class DailyLimiter {

    private record Count(LocalDate day, int count) {
    }

    private final int limit;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Count> counts = new ConcurrentHashMap<>();

    public DailyLimiter(int limit, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive");
        }
        this.limit = limit;
        this.clock = clock;
    }

    public int limit() {
        return limit;
    }

    /**
     * Counts one use for the key unless today's limit is reached.
     *
     * @return empty if allowed; otherwise when the limit resets (next 00:00 UTC)
     */
    public Optional<Instant> tryAcquire(long key) {
        LocalDate today = today();
        boolean[] blocked = {false};
        counts.compute(key, (k, c) -> {
            Count current = c == null || !c.day().equals(today) ? new Count(today, 0) : c;
            if (current.count() >= limit) {
                blocked[0] = true;
                return current;
            }
            return new Count(today, current.count() + 1);
        });
        return blocked[0] ? Optional.of(nextReset()) : Optional.empty();
    }

    /** Gives back one use, e.g. when the request it stood for failed. */
    public void release(long key) {
        LocalDate today = today();
        counts.computeIfPresent(key, (k, c) ->
                c.day().equals(today) && c.count() > 1 ? new Count(today, c.count() - 1) : null);
    }

    public Instant nextReset() {
        return today().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Drops counts from previous days. */
    public void cleanUp() {
        LocalDate today = today();
        counts.values().removeIf(c -> !c.day().equals(today));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
