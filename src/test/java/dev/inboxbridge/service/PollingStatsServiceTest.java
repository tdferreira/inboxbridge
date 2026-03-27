package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.UserPollingStatsView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.SourcePollingStateView;
import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.persistence.SourcePollEvent;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserBridge;
import dev.inboxbridge.persistence.UserBridgeRepository;

class PollingStatsServiceTest {

    @Test
    void userStatsStayScopedToOneUserDestinationKey() {
        PollingStatsService service = new PollingStatsService();
        service.importedMessageRepository = new ImportedMessageRepository() {
            @Override
            public long countByDestinationKey(String destinationKey) {
                return "user-gmail:7".equals(destinationKey) ? 3L : 0L;
            }

            @Override
            public List<Object[]> summarizeByImportedDayForDestinationKey(String destinationKey) {
                return java.util.Collections.singletonList(
                        new Object[] { Timestamp.from(Instant.parse("2026-03-26T00:00:00Z")), Long.valueOf(3) });
            }

            @Override
            public List<Instant> listImportedAtSinceForDestinationKey(String destinationKey, Instant since) {
                return List.of(
                        Instant.parse("2026-03-26T10:15:00Z"),
                        Instant.parse("2026-03-26T11:15:00Z"),
                        Instant.parse("2026-03-26T12:15:00Z"));
            }
        };
        service.userBridgeRepository = new UserBridgeRepository() {
            @Override
            public List<UserBridge> listByUserId(Long userId) {
                UserBridge bridge = new UserBridge();
                bridge.userId = userId;
                bridge.bridgeId = "user-fetcher";
                bridge.enabled = true;
                return List.of(bridge);
            }
        };
        service.sourcePollEventService = new SourcePollEventService() {
            @Override
            public Optional<dev.inboxbridge.dto.AdminPollEventSummary> latestForSource(String sourceId) {
                return Optional.empty();
            }

            @Override
            public List<SourcePollEvent> listBySourceIdsSince(List<String> sourceIds, Instant since) {
                return List.of();
            }
        };
        service.envSourceService = new EnvSourceService();
        service.sourcePollingStateService = new SourcePollingStateService() {
            @Override
            public Map<String, SourcePollingStateView> viewBySourceIds(List<String> sourceIds) {
                return Map.of();
            }
        };

        UserPollingStatsView stats = service.userStats(7L);

        assertEquals(3L, stats.totalImportedMessages());
        assertEquals(1, stats.configuredMailFetchers());
        assertEquals(1, stats.enabledMailFetchers());
        assertEquals(0L, stats.errorPolls());
        assertEquals(1, stats.health().activeMailFetchers());
        assertEquals(1, stats.providerBreakdown().getFirst().count());
        assertEquals(7, stats.importsByDay().size());
        assertEquals(3L, stats.importsByDay().stream()
                .mapToLong(dev.inboxbridge.dto.ImportTimelinePointView::importedMessages)
                .sum());
        assertEquals(24, stats.importTimelines().get("today").size());
        assertEquals(30, stats.importTimelines().get("pastMonth").size());
        assertEquals(12, stats.importTimelines().get("pastYear").size());
    }

    @Test
    void sourceStatsStayScopedToOneMailFetcher() {
        PollingStatsService service = new PollingStatsService();
        service.importedMessageRepository = new ImportedMessageRepository() {
            @Override
            public long countByDestinationKeyAndSourceAccountId(String destinationKey, String sourceAccountId) {
                return "user-gmail:7".equals(destinationKey) && "outlook-main".equals(sourceAccountId) ? 4L : 0L;
            }

            @Override
            public List<Object[]> summarizeByImportedDayForDestinationKeyAndSourceAccountId(String destinationKey, String sourceAccountId) {
                return java.util.Collections.singletonList(
                        new Object[] { Timestamp.from(Instant.parse("2026-03-26T00:00:00Z")), Long.valueOf(4) });
            }

            @Override
            public List<Instant> listImportedAtSinceForDestinationKeyAndSourceAccountId(String destinationKey, String sourceAccountId, Instant since) {
                return List.of(
                        Instant.parse("2026-03-26T10:15:00Z"),
                        Instant.parse("2026-03-26T11:15:00Z"),
                        Instant.parse("2026-03-26T12:15:00Z"),
                        Instant.parse("2026-03-26T13:15:00Z"));
            }
        };
        service.sourcePollEventService = new SourcePollEventService() {
            @Override
            public Optional<dev.inboxbridge.dto.AdminPollEventSummary> latestForSource(String sourceId) {
                return Optional.empty();
            }

            @Override
            public List<SourcePollEvent> listBySourceIdsSince(List<String> sourceIds, Instant since) {
                SourcePollEvent event = new SourcePollEvent();
                event.sourceId = "outlook-main";
                event.triggerName = "app-fetcher";
                event.status = "SUCCESS";
                event.startedAt = Instant.parse("2026-03-26T10:00:00Z");
                event.finishedAt = Instant.parse("2026-03-26T10:00:02Z");
                event.fetchedCount = 4;
                event.importedCount = 4;
                event.duplicateCount = 0;
                return List.of(event);
            }
        };
        service.sourcePollingStateService = new SourcePollingStateService() {
            @Override
            public Map<String, SourcePollingStateView> viewBySourceIds(List<String> sourceIds) {
                return Map.of();
            }
        };

        SourcePollingStatsView stats = service.sourceStats(new RuntimeBridge(
                "outlook-main",
                "USER",
                7L,
                "alice",
                true,
                dev.inboxbridge.config.BridgeConfig.Protocol.IMAP,
                "outlook.office365.com",
                993,
                true,
                dev.inboxbridge.config.BridgeConfig.AuthMethod.OAUTH2,
                dev.inboxbridge.config.BridgeConfig.OAuthProvider.MICROSOFT,
                "alice@example.com",
                "",
                "",
                Optional.of("INBOX"),
                false,
                Optional.of("Outlook"),
                new GmailTarget("user-gmail:7", 7L, "alice", "me", "client", "secret", "refresh", "https://localhost", true, false, false)));

        assertEquals(4L, stats.totalImportedMessages());
        assertEquals(1, stats.configuredMailFetchers());
        assertEquals(1, stats.enabledMailFetchers());
        assertEquals(0L, stats.errorPolls());
        assertEquals(1, stats.health().activeMailFetchers());
        assertEquals("Microsoft", stats.providerBreakdown().getFirst().label());
        assertEquals(1L, stats.manualRuns());
        assertEquals(0L, stats.scheduledRuns());
    }
}
