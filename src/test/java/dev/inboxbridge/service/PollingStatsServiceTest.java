package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.UserPollingStatsView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.SourcePollingStateView;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.SourcePollEvent;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;

class PollingStatsServiceTest {

    @Test
    void userStatsStayScopedToOneUserDestinationKey() {
        Instant importDay = LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        PollingStatsService service = new PollingStatsService();
        service.importedMessageRepository = new ImportedMessageRepository() {
            @Override
            public long countByDestinationKey(String destinationKey) {
                return "user-gmail:7".equals(destinationKey) ? 3L : 0L;
            }

            @Override
            public List<Instant> listImportedAtSinceForDestinationKey(String destinationKey, Instant since) {
                return List.of(
                        importDay.plusSeconds(10 * 3600L + 15 * 60L),
                        importDay.plusSeconds(11 * 3600L + 15 * 60L),
                        importDay.plusSeconds(12 * 3600L + 15 * 60L));
            }
        };
        service.userEmailAccountRepository = new UserEmailAccountRepository() {
            @Override
            public List<UserEmailAccount> listByUserId(Long userId) {
                UserEmailAccount bridge = new UserEmailAccount();
                bridge.userId = userId;
                bridge.emailAccountId = "user-fetcher";
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
        assertEquals(168, stats.importTimelines().get("pastWeek").size());
        assertEquals(30, stats.importTimelines().get("pastMonth").size());
        assertEquals(13, stats.importTimelines().get("pastTrimester").size());
        assertEquals(26, stats.importTimelines().get("pastSemester").size());
        assertEquals(52, stats.importTimelines().get("pastYear").size());
    }

    @Test
    void sourceStatsStayScopedToOneMailFetcher() {
        Instant importDay = LocalDate.now(ZoneOffset.UTC).minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        PollingStatsService service = new PollingStatsService();
        service.importedMessageRepository = new ImportedMessageRepository() {
            @Override
            public long countByDestinationKeyAndSourceAccountId(String destinationKey, String sourceAccountId) {
                return "user-gmail:7".equals(destinationKey) && "outlook-main".equals(sourceAccountId) ? 4L : 0L;
            }

            @Override
            public List<Instant> listImportedAtSinceForDestinationKeyAndSourceAccountId(String destinationKey, String sourceAccountId, Instant since) {
                return List.of(
                        importDay.plusSeconds(10 * 3600L + 15 * 60L),
                        importDay.plusSeconds(11 * 3600L + 15 * 60L),
                        importDay.plusSeconds(12 * 3600L + 15 * 60L),
                        importDay.plusSeconds(13 * 3600L + 15 * 60L));
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

        SourcePollingStatsView stats = service.sourceStats(new RuntimeEmailAccount(
                "outlook-main",
                "USER",
                7L,
                "alice",
                true,
                dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP,
                "outlook.office365.com",
                993,
                true,
                dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.OAUTH2,
                dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.MICROSOFT,
                "alice@example.com",
                "",
                "",
                Optional.of("INBOX"),
                false,
                Optional.of("Outlook"),
                new GmailApiDestinationTarget("user-gmail:7", 7L, "alice", UserMailDestinationConfigService.PROVIDER_GMAIL, "me", "client", "secret", "refresh", "https://localhost", true, false, false)));

        assertEquals(4L, stats.totalImportedMessages());
        assertEquals(1, stats.configuredMailFetchers());
        assertEquals(1, stats.enabledMailFetchers());
        assertEquals(0L, stats.errorPolls());
        assertEquals(1, stats.health().activeMailFetchers());
        assertEquals("Microsoft", stats.providerBreakdown().getFirst().label());
        assertEquals(1L, stats.manualRuns());
        assertEquals(0L, stats.scheduledRuns());
    }

    @Test
    void timelineBucketsFollowRequestedTimezone() {
        Instant importAt = Instant.parse("2026-04-04T09:15:00Z");
        PollingStatsService service = new PollingStatsService();
        service.importedMessageRepository = new ImportedMessageRepository() {
            @Override
            public long countByDestinationKey(String destinationKey) {
                return 1L;
            }

            @Override
            public List<Instant> listImportedAtSinceForDestinationKey(String destinationKey, Instant since) {
                return List.of(importAt);
            }
        };
        service.userEmailAccountRepository = new UserEmailAccountRepository() {
            @Override
            public List<UserEmailAccount> listByUserId(Long userId) {
                UserEmailAccount bridge = new UserEmailAccount();
                bridge.userId = userId;
                bridge.emailAccountId = "user-fetcher";
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

        UserPollingStatsView utcStats = service.userStats(7L, ZoneOffset.UTC);
        UserPollingStatsView lisbonStats = service.userStats(7L, ZoneId.of("Europe/Lisbon"));

        long utcNineAm = utcStats.importTimelines().get("today").stream()
                .filter(point -> "09:00".equals(point.bucketLabel()))
                .findFirst()
                .orElseThrow()
                .importedMessages();
        long lisbonTenAm = lisbonStats.importTimelines().get("today").stream()
                .filter(point -> "10:00".equals(point.bucketLabel()))
                .findFirst()
                .orElseThrow()
                .importedMessages();

        assertEquals(1L, utcNineAm);
        assertEquals(1L, lisbonTenAm);
    }

    @Test
    void customTimelineKeepsHourlyBucketsForRangesUpToOneWeek() {
        Instant importAt = Instant.parse("2026-03-04T09:15:00Z");
        PollingStatsService service = new PollingStatsService();
        service.importedMessageRepository = new ImportedMessageRepository() {
            @Override
            public List<Instant> listImportedAtSince(Instant since) {
                return List.of(importAt);
            }
        };
        service.sourcePollEventService = new SourcePollEventService() {
            @Override
            public List<SourcePollEvent> listSince(Instant since) {
                return List.of();
            }
        };

        PollingTimelineBundleView timeline = service.globalTimelineBundle(
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-07T23:00:00Z"),
                ZoneOffset.UTC);

        assertEquals(168, timeline.importTimelines().get("custom").size());
    }
}
