package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.PollThrottleLease;
import dev.inboxbridge.persistence.PollThrottleLeaseRepository;
import dev.inboxbridge.persistence.PollThrottleState;
import dev.inboxbridge.persistence.PollThrottleStateRepository;

class PollThrottleServiceTest {

    @Test
    void throttlesRepeatedSourceAccessPerHost() {
        RecordingPollThrottleService service = service();

        PollThrottleService.ThrottleLease first = service.acquireSourceMailboxPermit(source("imap.example.com"));
        service.release(first);
        PollThrottleService.ThrottleLease second = service.acquireSourceMailboxPermit(source("imap.example.com"));
        service.release(second);

        assertEquals(List.of(Duration.ofSeconds(1)), service.pauses);
    }

    @Test
    void enforcesSourceHostConcurrencyCapUntilLeaseReleases() {
        RecordingPollThrottleService service = service();

        PollThrottleService.ThrottleLease first = service.acquireSourceMailboxPermit(source("imap.example.com"));
        PollThrottleService.ThrottleLease second = service.acquireSourceMailboxPermit(source("imap.example.com"));
        service.release(first);
        service.release(second);

        assertEquals(List.of(Duration.ofMinutes(2)), service.pauses);
        assertEquals(2, service.stateRepository.findByThrottleKey("source-host:imap.example.com").orElseThrow().adaptiveMultiplier);
    }

    @Test
    void throttlesRepeatedGmailDeliveryAcrossAccounts() {
        RecordingPollThrottleService service = service();

        PollThrottleService.ThrottleLease first = service.acquireDestinationDeliveryPermit(gmailTarget(1L));
        service.release(first);
        PollThrottleService.ThrottleLease second = service.acquireDestinationDeliveryPermit(gmailTarget(2L));
        service.release(second);

        assertEquals(List.of(Duration.ofMillis(250)), service.pauses);
    }

    @Test
    void adaptiveStatePersistsRateLimitPenalty() {
        RecordingPollThrottleService service = service();
        RuntimeEmailAccount emailAccount = source("imap.example.com");

        service.recordSourceFailure(emailAccount, "429 too many requests");

        PollThrottleState state = service.stateRepository.findByThrottleKey("source-host:imap.example.com").orElseThrow();
        assertEquals(3, state.adaptiveMultiplier);
        assertNotNull(state.nextAllowedAt);
    }

    @Test
    void successDecaysAdaptiveMultiplierTowardOne() {
        RecordingPollThrottleService service = service();
        RuntimeEmailAccount emailAccount = source("imap.example.com");

        service.recordSourceFailure(emailAccount, "429 too many requests");
        service.recordSourceSuccess(emailAccount);

        PollThrottleState state = service.stateRepository.findByThrottleKey("source-host:imap.example.com").orElseThrow();
        assertEquals(2, state.adaptiveMultiplier);
    }

    private RecordingPollThrottleService service() {
        RecordingPollThrottleService service = new RecordingPollThrottleService();
        service.pollingSettingsService = new FakePollingSettingsService();
        service.stateRepository = new InMemoryPollThrottleStateRepository();
        service.leaseRepository = new InMemoryPollThrottleLeaseRepository();
        return service;
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
        private Instant currentTime = Instant.parse("2026-03-30T10:00:00Z");
        private final List<Duration> pauses = new ArrayList<>();

        @Override
        protected Instant now() {
            return currentTime;
        }

        @Override
        protected void pause(Duration duration) {
            pauses.add(duration);
            currentTime = currentTime.plus(duration);
        }
    }

    private static final class InMemoryPollThrottleStateRepository extends PollThrottleStateRepository {
        private final List<PollThrottleState> states = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public Optional<PollThrottleState> findByThrottleKey(String throttleKey) {
            return states.stream().filter(state -> throttleKey.equals(state.throttleKey)).findFirst();
        }

        @Override
        public Optional<PollThrottleState> findByThrottleKeyForUpdate(String throttleKey) {
            return findByThrottleKey(throttleKey);
        }

        @Override
        public void persist(PollThrottleState entity) {
            if (entity.id == null) {
                entity.id = sequence++;
                states.add(entity);
                return;
            }
            assertTrue(states.stream().anyMatch(existing -> existing.id.equals(entity.id)));
        }

        @Override
        public void flush() {
        }
    }

    private static final class InMemoryPollThrottleLeaseRepository extends PollThrottleLeaseRepository {
        private final List<PollThrottleLease> leases = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public long deleteExpired(String throttleKey, Instant now) {
            int before = leases.size();
            leases.removeIf(lease -> throttleKey.equals(lease.throttleKey) && !lease.expiresAt.isAfter(now));
            return before - leases.size();
        }

        @Override
        public long countActive(String throttleKey, Instant now) {
            return leases.stream()
                    .filter(lease -> throttleKey.equals(lease.throttleKey) && lease.expiresAt.isAfter(now))
                    .count();
        }

        @Override
        public Optional<Instant> earliestActiveExpiry(String throttleKey, Instant now) {
            return leases.stream()
                    .filter(lease -> throttleKey.equals(lease.throttleKey) && lease.expiresAt.isAfter(now))
                    .min(Comparator.comparing(lease -> lease.expiresAt))
                    .map(lease -> lease.expiresAt);
        }

        @Override
        public long deleteByLeaseToken(String leaseToken) {
            int before = leases.size();
            leases.removeIf(lease -> leaseToken.equals(lease.leaseToken));
            return before - leases.size();
        }

        @Override
        public void persist(PollThrottleLease entity) {
            if (entity.id == null) {
                entity.id = sequence++;
                leases.add(entity);
            }
        }
    }

    private static final class FakePollingSettingsService extends PollingSettingsService {
        @Override
        public EffectiveThrottleSettings effectiveThrottleSettings() {
            return new EffectiveThrottleSettings(
                    Duration.ofSeconds(1),
                    1,
                    Duration.ofMillis(250),
                    1,
                    Duration.ofMinutes(2),
                    6,
                    0.2d,
                    Duration.ofSeconds(30));
        }
    }
}
