package tn.iteam.logging;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limited logger to prevent log spam during prolonged Redis unavailability.
 * 
 * Behavior:
 * - First occurrence: logs at WARN level
 * - Subsequent occurrences within cooldown: logs at DEBUG level
 * - After cooldown expires: logs WARN again (to signal continued issue)
 * - When issue resolves: logs INFO
 */
@Slf4j
public class RateLimitedLogger {

    private final String category;
    private final Map<String, LogState> states = new ConcurrentHashMap<>();
    private final Duration warnCooldown;
    private final Duration staleWarningInterval;

    public RateLimitedLogger(String category) {
        this(category, Duration.ofMinutes(5), Duration.ofMinutes(10));
    }

    public RateLimitedLogger(String category, Duration warnCooldown, Duration staleWarningInterval) {
        this.category = category;
        this.warnCooldown = warnCooldown;
        this.staleWarningInterval = staleWarningInterval;
    }

    /**
     * Log a warning that occurs once, then switches to debug during cooldown.
     */
    public void warnOnce(String key, String message) {
        warnOnce(key, message, null);
    }

    public void warnOnce(String key, String message, Throwable t) {
        Instant now = Instant.now();
        LogState state = states.compute(key, (k, existing) -> {
            if (existing == null) {
                return new LogState(now, now, LogLevel.WARN, 1);
            }

            Duration sinceLastWarn = Duration.between(existing.lastWarnAt, now);
            Duration sinceLastLog = Duration.between(existing.lastLogAt, now);

            if (existing.level == LogLevel.WARN && sinceLastWarn.compareTo(warnCooldown) < 0) {
                // Within cooldown period - log at DEBUG
                log.debug("[{}] {} - {}", category, key, message);
                return new LogState(existing.firstOccurrence, now, LogLevel.DEBUG, existing.occurrenceCount + 1);
            } else if (existing.level == LogLevel.DEBUG && sinceLastLog.compareTo(warnCooldown) < 0) {
                // Still in DEBUG mode
                log.debug("[{}] {} - {}", category, key, message);
                return new LogState(existing.firstOccurrence, now, LogLevel.DEBUG, existing.occurrenceCount + 1);
            } else {
                // Cooldown expired - log WARN again
                if (t != null) {
                    log.warn("[{}] {} - {} (occurrence #{})", category, key, message, existing.occurrenceCount + 1);
                } else {
                    log.warn("[{}] {} - {} (occurrence #{})", category, key, message, existing.occurrenceCount + 1);
                }
                return new LogState(existing.firstOccurrence, now, LogLevel.WARN, existing.occurrenceCount + 1);
            }
        });
    }

    /**
     * Log a stale data warning (less frequent than regular warnings).
     */
    public void warnStale(String key, String message) {
        Instant now = Instant.now();
        LogState state = states.compute(key, (k, existing) -> {
            if (existing == null || Duration.between(existing.lastLogAt, now).compareTo(staleWarningInterval) >= 0) {
                log.warn("[{}] STALE: {} - {}", category, key, message);
                return new LogState(now, now, LogLevel.WARN, 1);
            }
            log.debug("[{}] STALE: {} - {}", category, key, message);
            return existing;
        });
    }

    /**
     * Log recovery (Redis back online).
     */
    public void info(String key, String message) {
        log.info("[{}] {} - {}", category, key, message);
        states.remove(key); // Clear state on recovery
    }

    /**
     * Debug log that always goes through.
     */
    public void debug(String key, String message) {
        log.debug("[{}] {} - {}", category, key, message);
    }

    /**
     * Clear all state (useful for testing).
     */
    public void clear() {
        states.clear();
    }

    /**
     * Get current state for monitoring.
     */
    public Map<String, LogState> getStates() {
        return Map.copyOf(states);
    }

    private enum LogLevel {
        WARN, DEBUG
    }

    private record LogState(Instant firstOccurrence, Instant lastLogAt, LogLevel level, int occurrenceCount) {
    }
}