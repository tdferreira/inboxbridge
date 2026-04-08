package dev.inboxbridge.service.mail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ImapIdleHealthServiceTest {

    @Test
    void schedulerFallbackStartsOnlyAfterTheConfiguredUnhealthyThreshold() {
        ImapIdleHealthService service = new ImapIdleHealthService();
        Instant baseline = Instant.parse("2026-04-05T08:00:00Z");

        service.ensureTracked("idle-source", baseline);

        assertFalse(service.shouldSchedulerFallback("idle-source", baseline.plusSeconds(30)));
        assertTrue(service.shouldSchedulerFallback(
                "idle-source",
                baseline.plus(ImapIdleHealthService.SCHEDULER_FALLBACK_THRESHOLD).plusSeconds(1)));
    }

    @Test
    void reconnectingToIdleClearsSchedulerFallbackState() {
        ImapIdleHealthService service = new ImapIdleHealthService();
        Instant baseline = Instant.parse("2026-04-05T08:00:00Z");

        service.ensureTracked("idle-source", baseline);
        service.markDisconnected("idle-source", baseline);
        assertTrue(service.shouldSchedulerFallback(
                "idle-source",
                baseline.plus(ImapIdleHealthService.SCHEDULER_FALLBACK_THRESHOLD).plusSeconds(1)));

        service.markConnected("idle-source", baseline.plusSeconds(10));

        assertTrue(service.isHealthy("idle-source"));
        assertFalse(service.shouldSchedulerFallback(
                "idle-source",
                baseline.plus(ImapIdleHealthService.SCHEDULER_FALLBACK_THRESHOLD).plusSeconds(30)));
    }
}
