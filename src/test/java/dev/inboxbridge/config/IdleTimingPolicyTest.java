package dev.inboxbridge.config;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdleTimingPolicyTest {
    @Test
    void renewalRejectsUnsafeBoundariesWithoutOverflow() {
        var minute = Duration.ofMinutes(1);
        for (Duration interval : new Duration[] {null, Duration.ZERO, Duration.ofNanos(-1), minute.minusNanos(1)})
            assertThrows(IllegalArgumentException.class, () -> IdleTimingPolicy.validateRenewal(interval, Duration.ofSeconds(20)));
        for (Duration timeout : new Duration[] {null, Duration.ZERO, Duration.ofNanos(-1), Duration.ofMinutes(29), Duration.ofSeconds(Long.MAX_VALUE), Duration.ofMinutes(28)})
            assertThrows(IllegalArgumentException.class, () -> IdleTimingPolicy.validateRenewal(minute, timeout));
        assertThrows(IllegalArgumentException.class, () -> IdleTimingPolicy.validateRenewal(Duration.ofSeconds(Long.MAX_VALUE), Duration.ofSeconds(20)));
        assertDoesNotThrow(() -> IdleTimingPolicy.validateRenewal(minute, Duration.ofSeconds(20)));
        assertDoesNotThrow(() -> IdleTimingPolicy.validateRenewal(minute, Duration.ofMinutes(28).minusNanos(1)));
    }
    @Test
    void safetyHasInclusiveUpperAndStrictLowerBounds() {
        for (Duration safety : new Duration[] {null, Duration.ZERO, Duration.ofNanos(-1), Duration.ofDays(1).plusNanos(1), Duration.ofSeconds(Long.MAX_VALUE)})
            assertThrows(IllegalArgumentException.class, () -> IdleTimingPolicy.validateSafetyRange(safety));
        assertEquals(Duration.ofDays(1), IdleTimingPolicy.validateSafetyRange(Duration.ofDays(1)));
        var renewal = Duration.ofMinutes(1); var timeout = Duration.ofSeconds(20);
        assertThrows(IllegalArgumentException.class, () -> IdleTimingPolicy.validateSafety(renewal, renewal, timeout));
        assertThrows(IllegalArgumentException.class, () -> IdleTimingPolicy.validateSafety(renewal.plus(timeout), renewal, timeout));
        assertDoesNotThrow(() -> IdleTimingPolicy.validateSafety(renewal.plus(timeout).plusNanos(1), renewal, timeout));
        assertDoesNotThrow(() -> IdleTimingPolicy.validateSafety(Duration.ofDays(1), renewal, timeout));
    }
}
