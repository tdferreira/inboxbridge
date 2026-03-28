package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.persistence.SourcePollingStateRepository;

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
        Instant finishedAt = Instant.parse("2026-03-26T12:00:00Z");

        service.recordSuccess("fetcher-1", finishedAt, settings(true, "3m", 25));

        var state = service.viewForSource("fetcher-1").orElseThrow();
        assertEquals(Instant.parse("2026-03-26T12:03:00Z"), state.nextPollAt());
        assertEquals(0, state.consecutiveFailures());
        assertEquals(finishedAt, state.lastSuccessAt());
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

    private SourcePollingStateService service() {
        SourcePollingStateService service = new SourcePollingStateService();
        service.repository = new InMemorySourcePollingStateRepository();
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
}
