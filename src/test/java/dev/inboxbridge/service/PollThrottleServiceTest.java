package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;

class PollThrottleServiceTest {

    @Test
    void throttlesRepeatedSourceAccessPerHost() {
        RecordingPollThrottleService service = new RecordingPollThrottleService();

        service.awaitSourceMailboxTurn(source("imap.example.com"));
        service.awaitSourceMailboxTurn(source("imap.example.com"));

        assertEquals(List.of(Duration.ofSeconds(1)), service.pauses);
    }

    @Test
    void throttlesRepeatedGmailDeliveryAcrossAccounts() {
        RecordingPollThrottleService service = new RecordingPollThrottleService();

        service.awaitDestinationDeliveryTurn(gmailTarget(1L));
        service.awaitDestinationDeliveryTurn(gmailTarget(2L));

        assertEquals(List.of(Duration.ofMillis(250)), service.pauses);
    }

    private RuntimeEmailAccount source(String host) {
        return new RuntimeEmailAccount(
                "source-" + host,
                "USER",
                1L,
                "john",
                true,
                dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP,
                host,
                993,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.PASSWORD,
                dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.NONE,
                "john@example.com",
                "secret",
                "",
                java.util.Optional.of("INBOX"),
                false,
                java.util.Optional.empty(),
                gmailTarget(1L));
    }

    private GmailApiDestinationTarget gmailTarget(Long userId) {
        return new GmailApiDestinationTarget(
                "gmail:" + userId,
                userId,
                "john",
                UserMailDestinationConfigService.PROVIDER_GMAIL,
                "me",
                "client",
                "secret",
                "refresh",
                "https://localhost",
                true,
                false,
                false);
    }

    private static final class RecordingPollThrottleService extends PollThrottleService {
        private final Deque<Instant> timeline = new ArrayDeque<>(List.of(
                Instant.parse("2026-03-30T10:00:00Z"),
                Instant.parse("2026-03-30T10:00:00Z"),
                Instant.parse("2026-03-30T10:00:00Z"),
                Instant.parse("2026-03-30T10:00:00Z")));
        private final List<Duration> pauses = new java.util.ArrayList<>();

        RecordingPollThrottleService() {
            this.inboxBridgeConfig = new FakeInboxBridgeConfig();
        }

        @Override
        protected Instant now() {
            return timeline.isEmpty() ? Instant.parse("2026-03-30T10:00:00Z") : timeline.removeFirst();
        }

        @Override
        protected void pause(Duration duration) {
            pauses.add(duration);
        }
    }

    private static final class FakeInboxBridgeConfig implements dev.inboxbridge.config.InboxBridgeConfig {
        @Override
        public boolean pollEnabled() { return true; }
        @Override
        public String pollInterval() { return "5m"; }
        @Override
        public int fetchWindow() { return 50; }
        @Override
        public Duration sourceHostMinSpacing() { return Duration.ofSeconds(1); }
        @Override
        public Duration destinationProviderMinSpacing() { return Duration.ofMillis(250); }
        @Override
        public double successJitterRatio() { return 0.2d; }
        @Override
        public Duration maxSuccessJitter() { return Duration.ofSeconds(30); }
        @Override
        public boolean multiUserEnabled() { return true; }
        @Override
        public Security security() { throw new UnsupportedOperationException(); }
        @Override
        public Gmail gmail() { throw new UnsupportedOperationException(); }
        @Override
        public Microsoft microsoft() { throw new UnsupportedOperationException(); }
        @Override
        public List<dev.inboxbridge.config.InboxBridgeConfig.Source> sources() { return List.of(); }
    }
}
