package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.domain.ImapCheckpoint;
import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.persistence.SourcePollingStateRepository;
import jakarta.transaction.Transactional;

class SourcePollingStateServiceTest {

    @Test
    void eligibilityStartsReadyWhenNoPriorStateExists() {
        SourcePollingStateService service = service();

        SourcePollingStateService.PollEligibility eligibility = service.eligibility(
                "fetcher-1",
                settings(true, "5m", 25),
                Instant.parse("2026-03-26T12:00:00Z"),
                false);

        assertTrue(eligibility.shouldPoll());
        assertEquals("READY", eligibility.reason());
    }

    @Test
    void successSchedulesNextPollFromEffectiveInterval() {
        SourcePollingStateService service = service();
        Instant finishedAt = Instant.parse("2026-03-26T12:01:17Z");

        service.recordSuccess("fetcher-1", finishedAt, settings(true, "3m", 25));

        var state = service.viewForSource("fetcher-1").orElseThrow();
        assertEquals(Instant.parse("2026-03-26T12:03:00Z"), state.nextPollAt());
        assertEquals(0, state.consecutiveFailures());
        assertEquals(finishedAt, state.lastSuccessAt());
    }

    @Test
    void successSchedulesOnClockAlignedBoundaries() {
        SourcePollingStateService service = service();

        assertEquals(
                Instant.parse("2026-03-26T01:02:00Z"),
                service.nextAlignedPollAt(Instant.parse("2026-03-26T01:00:31Z"), Duration.ofMinutes(2)));
        assertEquals(
                Instant.parse("2026-03-26T04:05:00Z"),
                service.nextAlignedPollAt(Instant.parse("2026-03-26T04:03:12Z"), Duration.ofMinutes(5)));
        assertEquals(
                Instant.parse("2026-03-26T03:10:00Z"),
                service.nextAlignedPollAt(Instant.parse("2026-03-26T03:04:55Z"), Duration.ofMinutes(10)));
    }

    @Test
    void repeatedRateLimitFailuresIncreaseCooldown() {
        SourcePollingStateService service = service();

        service.recordFailure("fetcher-1", Instant.parse("2026-03-26T12:00:00Z"), "429 too many requests");
        service.recordFailure("fetcher-1", Instant.parse("2026-03-26T12:05:00Z"), "429 too many requests");

        var state = service.viewForSource("fetcher-1").orElseThrow();
        assertEquals(2, state.consecutiveFailures());
        assertEquals(Instant.parse("2026-03-26T12:35:00Z"), state.cooldownUntil());
        assertEquals("429 too many requests", state.lastFailureReason());
    }

    @Test
    void oauthAuthorizationFailuresUseLongAuthCooldown() {
        SourcePollingStateService service = service();

        service.recordFailure("fetcher-1", Instant.parse("2026-03-26T12:00:00Z"), "invalid_grant");

        var state = service.viewForSource("fetcher-1").orElseThrow();
        assertEquals(Instant.parse("2026-03-26T12:30:00Z"), state.cooldownUntil());
    }

    @Test
    void providerAvailabilityFailuresUseTransientCooldownTier() {
        SourcePollingStateService service = service();

        service.recordFailure("fetcher-1", Instant.parse("2026-03-26T12:00:00Z"), "503 service unavailable");

        var state = service.viewForSource("fetcher-1").orElseThrow();
        assertEquals(Instant.parse("2026-03-26T12:05:00Z"), state.cooldownUntil());
    }

    @Test
    void failureReturnsCooldownDecisionAuditDetails() {
        SourcePollingStateService service = service();

        SourcePollingStateService.CooldownDecision decision = service.recordFailure(
                "fetcher-1",
                Instant.parse("2026-03-26T12:00:00Z"),
                "429 too many requests");

        assertEquals("RATE_LIMIT", decision.failureCategory());
        assertEquals(1, decision.consecutiveFailures());
        assertEquals(Duration.ofMinutes(15), decision.backoff());
        assertEquals(Instant.parse("2026-03-26T12:15:00Z"), decision.cooldownUntil());
    }

    @Test
    void cooldownBlocksPollingUntilExpiry() {
        SourcePollingStateService service = service();
        service.recordFailure("fetcher-1", Instant.parse("2026-03-26T12:00:00Z"), "invalid_grant");

        SourcePollingStateService.PollEligibility eligibility = service.eligibility(
                "fetcher-1",
                settings(true, "5m", 25),
                Instant.parse("2026-03-26T12:10:00Z"),
                false);

        assertFalse(eligibility.shouldPoll());
        assertEquals("COOLDOWN", eligibility.reason());
        assertNotNull(eligibility.state());
    }

    @Test
    void cooldownCanBeIgnoredForExplicitManualSourceRuns() {
        SourcePollingStateService service = service();
        service.recordFailure("fetcher-1", Instant.parse("2026-03-26T12:00:00Z"), "invalid_grant");

        SourcePollingStateService.PollEligibility eligibility = service.eligibility(
                "fetcher-1",
                settings(true, "5m", 25),
                Instant.parse("2026-03-26T12:10:00Z"),
                true,
                true);

        assertTrue(eligibility.shouldPoll());
        assertEquals("READY", eligibility.reason());
    }

    @Test
    void disabledPollingSettingsBlockPollingImmediately() {
        SourcePollingStateService service = service();

        SourcePollingStateService.PollEligibility eligibility = service.eligibility(
                "fetcher-1",
                settings(false, "5m", 25),
                Instant.parse("2026-03-26T12:10:00Z"),
                false);

        assertFalse(eligibility.shouldPoll());
        assertEquals("DISABLED", eligibility.reason());
    }

    @Test
    void recordImapCheckpointStoresAndReturnsFolderCheckpoint() {
        SourcePollingStateService service = service();

        service.recordImapCheckpoint("fetcher-1", "user-destination:7", "INBOX", 44L, 101L, Instant.parse("2026-03-26T12:11:00Z"));

        ImapCheckpoint checkpoint = service.imapCheckpoint("fetcher-1", "user-destination:7", "inbox").orElseThrow();
        assertEquals("INBOX", checkpoint.folderName());
        assertEquals(44L, checkpoint.uidValidity());
        assertEquals(101L, checkpoint.lastSeenUid());
    }

    @Test
    void recordImapCheckpointOnlyMovesForwardForSameFolderAndUidValidity() {
        SourcePollingStateService service = service();

        service.recordImapCheckpoint("fetcher-1", "user-destination:7", "INBOX", 44L, 101L, Instant.parse("2026-03-26T12:11:00Z"));
        service.recordImapCheckpoint("fetcher-1", "user-destination:7", "INBOX", 44L, 99L, Instant.parse("2026-03-26T12:12:00Z"));
        service.recordImapCheckpoint("fetcher-1", "user-destination:7", "INBOX", 44L, 105L, Instant.parse("2026-03-26T12:13:00Z"));

        ImapCheckpoint checkpoint = service.imapCheckpoint("fetcher-1", "user-destination:7", "INBOX").orElseThrow();
        assertEquals(105L, checkpoint.lastSeenUid());
    }

    @Test
    void recordPopCheckpointStoresAndReturnsUidl() {
        SourcePollingStateService service = service();

        service.recordPopCheckpoint("fetcher-1", "user-destination:7", "uidl-101", Instant.parse("2026-03-26T12:14:00Z"));

        assertEquals("uidl-101", service.popCheckpoint("fetcher-1", "user-destination:7").orElseThrow());
    }

    @Test
    void checkpointsOnlyApplyToTheMatchingDestinationKey() {
        SourcePollingStateService service = service();

        service.recordImapCheckpoint("fetcher-1", "user-destination:7", "INBOX", 44L, 101L, Instant.parse("2026-03-26T12:11:00Z"));
        service.recordPopCheckpoint("fetcher-1", "user-destination:7", "uidl-101", Instant.parse("2026-03-26T12:14:00Z"));

        assertTrue(service.imapCheckpoint("fetcher-1", "user-destination:8", "INBOX").isEmpty());
        assertTrue(service.popCheckpoint("fetcher-1", "user-destination:8").isEmpty());
    }

    @Test
    void popCheckpointIsTransactionalForAsyncWorkerAccess() throws Exception {
        Method method = SourcePollingStateService.class.getDeclaredMethod("popCheckpoint", String.class, String.class);

        assertTrue(method.isAnnotationPresent(Transactional.class));
    }

    private SourcePollingStateService service() {
        SourcePollingStateService service = new SourcePollingStateService();
        service.repository = new InMemorySourcePollingStateRepository();
        service.pollingSettingsService = new FakePollingSettingsService();
        return service;
    }

    private PollingSettingsService.EffectivePollingSettings settings(boolean enabled, String intervalText, int fetchWindow) {
        long minutes = Long.parseLong(intervalText.substring(0, intervalText.length() - 1));
        return new PollingSettingsService.EffectivePollingSettings(enabled, intervalText, Duration.ofMinutes(minutes), fetchWindow);
    }

    private static final class InMemorySourcePollingStateRepository extends SourcePollingStateRepository {
        private final List<SourcePollingState> states = new ArrayList<>();
        private long sequence = 1L;

        @Override
        public Optional<SourcePollingState> findBySourceId(String sourceId) {
            return states.stream().filter(state -> sourceId.equals(state.sourceId)).findFirst();
        }

        @Override
        public Map<String, SourcePollingState> findBySourceIds(List<String> sourceIds) {
            return states.stream()
                    .filter(state -> sourceIds.contains(state.sourceId))
                    .collect(java.util.stream.Collectors.toMap(state -> state.sourceId, state -> state));
        }

        @Override
        public void persist(SourcePollingState entity) {
            if (entity.id == null) {
                entity.id = sequence++;
                states.add(entity);
            }
        }
    }

    private static final class FakePollingSettingsService extends PollingSettingsService {
        @Override
        public EffectiveThrottleSettings effectiveThrottleSettings() {
            return new EffectiveThrottleSettings(
                    Duration.ofSeconds(1),
                    2,
                    Duration.ofMillis(250),
                    1,
                    Duration.ofMinutes(2),
                    6,
                    0.2d,
                    Duration.ofSeconds(30));
        }
    }
}
