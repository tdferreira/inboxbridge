package dev.inboxbridge.config;

import java.time.Duration;

/** Shared production limits for environment defaults, inherited values and API overrides. */
public final class IdleTimingPolicy {
    public static final Duration MIN_RENEWAL_INTERVAL = Duration.ofMinutes(1);
    public static final Duration MAX_RENEWAL_WINDOW = Duration.ofMinutes(29);
    public static final Duration MAX_SAFETY_INTERVAL = Duration.ofDays(1);

    private IdleTimingPolicy() { }

    public static void validateRenewal(Duration interval, Duration timeout) {
        if (interval == null || interval.compareTo(MIN_RENEWAL_INTERVAL) < 0) {
            throw new IllegalArgumentException("IDLE renewal interval must be at least 1 minute");
        }
        // Compare bounded operands before any addition, including adversarial Duration.MAX values.
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(MAX_RENEWAL_WINDOW) >= 0
                || interval.compareTo(MAX_RENEWAL_WINDOW.minus(timeout)) >= 0) {
            throw new IllegalArgumentException("IDLE renewal timeout must be positive and the total renewal window less than 29 minutes");
        }
    }

    public static Duration validateSafetyRange(Duration safety) {
        if (safety == null || safety.isZero() || safety.isNegative() || safety.compareTo(MAX_SAFETY_INTERVAL) > 0) {
            throw new IllegalArgumentException("IDLE safety interval must be positive and at most 24 hours");
        }
        return safety;
    }

    public static void validateSafety(Duration safety, Duration renewal, Duration timeout) {
        validateRenewal(renewal, timeout);
        validateSafetyRange(safety);
        if (safety.compareTo(renewal.plus(timeout)) <= 0) {
            throw new IllegalArgumentException("IDLE safety interval must be greater than the renewal interval plus its response deadline");
        }
    }
}
