package dev.inboxbridge.service.mail;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class ImapIdleHealthServiceTest {

    private dev.inboxbridge.domain.RuntimeEmailAccount account() {
        return new dev.inboxbridge.domain.RuntimeEmailAccount("source", "SYSTEM", null, null, true,
                dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP, "imap.example.test", 993, true,
                dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.PASSWORD, dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.NONE,
                "synthetic@example.test", "synthetic", "", java.util.Optional.of("INBOX"), false,
                dev.inboxbridge.domain.SourceFetchMode.IDLE, java.util.Optional.empty(), null);
    }

    @Test
    void freshRenewalDefersSafetyDeadlineAndAStaleAcknowledgementTriggersIt() {
        var health = new ImapIdleHealthService();
        var account = account();
        Instant start = Instant.parse("2026-10-25T20:00:00Z");
        health.markConnected("source", "source\nINBOX", start);
        assertFalse(health.isSafetyPollDue(account, start.plusSeconds(29 * 60), null));
        assertTrue(health.isSafetyPollDue(account, start.plusSeconds(30 * 60), null));
        health.markRenewed("source", "source\nINBOX", start.plusSeconds(20 * 60));
        org.junit.jupiter.api.Assertions.assertEquals(start.plusSeconds(50 * 60), health.nextSafetyPollAt(account, null));
        assertFalse(health.isSafetyPollDue(account, start.plusSeconds(40 * 60), null));
        assertTrue(health.isSafetyPollDue(account, start.plusSeconds(50 * 60), null));
        // A completed safety sync must not cause another run on every scheduler tick.
        assertFalse(health.isSafetyPollDue(account, start.plusSeconds(51 * 60), start.plusSeconds(50 * 60)));
        health.safetyPollingEnabled = false;
        org.junit.jupiter.api.Assertions.assertNull(health.nextSafetyPollAt(account, null));
    }

    @Test
    void aFreshSiblingDoesNotHideAStaleFolder() {
        var health = new ImapIdleHealthService();
        Instant start = Instant.parse("2026-10-25T20:00:00Z");
        health.markConnected("source", "source\nINBOX", start);
        health.markConnected("source", "source\nJunk", start);
        health.markRenewed("source", "source\nINBOX", start.plusSeconds(29 * 60));
        assertTrue(health.isSafetyPollDue(account(), start.plusSeconds(30 * 60), null));
        health.markRenewed("source", "source\nJunk", start.plusSeconds(29 * 60));
        assertFalse(health.isSafetyPollDue(account(), start.plusSeconds(30 * 60), null));
    }

    @Test
    void renewalTimestampTracksAcknowledgementsAndResetsOnReconnect() {
        var service = new ImapIdleHealthService();
        Instant now = Instant.parse("2026-10-25T09:00:00Z");
        service.markConnected("source", "source\nINBOX", now);
        service.markRenewed("source", "source\nINBOX", now.plusSeconds(20));
        org.junit.jupiter.api.Assertions.assertEquals(now.plusSeconds(20), service.watchStates("source").getFirst().lastRenewedAt());
        service.markDisconnected("source", "source\nINBOX", now.plusSeconds(30));
        service.markRenewed("source", "source\nINBOX", now.plusSeconds(40));
        org.junit.jupiter.api.Assertions.assertEquals(now.plusSeconds(20), service.watchStates("source").getFirst().lastRenewedAt());
        service.markConnected("source", "source\nINBOX", now.plusSeconds(50));
        org.junit.jupiter.api.Assertions.assertNull(service.watchStates("source").getFirst().lastRenewedAt());
    }

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
