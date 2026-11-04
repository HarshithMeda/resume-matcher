package com.harshith.resumematcher.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "At most N events per key in any rolling window", kept in memory.
 * Each key stores the times of its recent events; older ones are dropped as the window moves.
 *
 * <p>Counts are per app instance and reset when the app restarts (see README, known limitations).
 */
public class SlidingWindowLimiter {

    private final int limit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Limit and window must be positive");
        }
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    public int limit() {
        return limit;
    }

    /**
     * Records one event for the key unless it's already at the limit.
     *
     * @return empty if allowed; otherwise how long until the oldest event leaves the window
     */
    public Optional<Duration> tryAcquire(String key) {
        Instant now = clock.instant();
        Duration[] wait = {null};
        events.compute(key, (k, times) -> {
            Deque<Instant> q = times == null ? new ArrayDeque<>() : times;
            prune(q, now);
            if (q.size() >= limit) {
                wait[0] = Duration.between(now, q.peekFirst().plus(window));
            } else {
                q.addLast(now);
            }
            return q;
        });
        return Optional.ofNullable(wait[0]);
    }

    /** Takes back the most recent event, e.g. when the attempt it stood for didn't count. */
    public void release(String key) {
        events.computeIfPresent(key, (k, q) -> {
            q.pollLast();
            return q.isEmpty() ? null : q;
        });
    }

    /** Forgets every event for the key. */
    public void reset(String key) {
        events.remove(key);
    }

    /** Drops expired events and empty keys so memory doesn't grow. */
    public void cleanUp() {
        Instant now = clock.instant();
        for (String key : events.keySet()) {
            events.computeIfPresent(key, (k, q) -> {
                prune(q, now);
                return q.isEmpty() ? null : q;
            });
        }
    }

    int trackedKeys() {
        return events.size();
    }

    private void prune(Deque<Instant> q, Instant now) {
        while (!q.isEmpty() && !q.peekFirst().plus(window).isAfter(now)) {
            q.pollFirst();
        }
    }
}
