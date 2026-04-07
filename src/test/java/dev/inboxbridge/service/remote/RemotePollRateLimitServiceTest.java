package dev.inboxbridge.service.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class RemotePollRateLimitServiceTest {

    @Test
    void bypassesRateLimitingWhenInputsAreEffectivelyDisabled() {
        RemotePollRateLimitService service = new RemotePollRateLimitService();
        Instant now = Instant.parse("2026-04-07T10:00:00Z");

        assertTrue(service.tryAcquire(null, 5, Duration.ofMinutes(1), now).allowed());
        assertTrue(service.tryAcquire("  ", 5, Duration.ofMinutes(1), now).allowed());
        assertTrue(service.tryAcquire("admin", 0, Duration.ofMinutes(1), now).allowed());
        assertTrue(service.tryAcquire("admin", 1, Duration.ZERO, now).allowed());
        assertTrue(service.tryAcquire("admin", 1, Duration.ofSeconds(-1), now).allowed());
    }

    @Test
    void rejectsRequestsThatExceedTheConfiguredWindowAndReturnsRetryTime() {
        RemotePollRateLimitService service = new RemotePollRateLimitService();
        Instant first = Instant.parse("2026-04-07T10:00:00Z");
        Duration window = Duration.ofMinutes(1);

        assertTrue(service.tryAcquire("admin", 2, window, first).allowed());
        assertTrue(service.tryAcquire("admin", 2, window, first.plusSeconds(10)).allowed());

        RemotePollRateLimitService.Decision denied = service.tryAcquire("admin", 2, window, first.plusSeconds(20));

        assertTrue(!denied.allowed());
        assertEquals(first.plus(window), denied.retryAt());
    }

    @Test
    void dropsExpiredInvocationsBeforeApplyingTheLimit() {
        RemotePollRateLimitService service = new RemotePollRateLimitService();
        Duration window = Duration.ofMinutes(1);
        Instant first = Instant.parse("2026-04-07T10:00:00Z");

        assertTrue(service.tryAcquire("admin", 1, window, first).allowed());

        RemotePollRateLimitService.Decision decision = service.tryAcquire("admin", 1, window, first.plusSeconds(61));

        assertTrue(decision.allowed());
        assertNull(decision.retryAt());
    }
}
