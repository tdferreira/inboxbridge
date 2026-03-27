package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.UserPollingStatsView;
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
        };
        service.envSourceService = new EnvSourceService();

        UserPollingStatsView stats = service.userStats(7L);

        assertEquals(3L, stats.totalImportedMessages());
        assertEquals(1, stats.configuredMailFetchers());
        assertEquals(1, stats.enabledMailFetchers());
        assertEquals(7, stats.importsByDay().size());
        assertEquals(3L, stats.importsByDay().stream()
                .mapToLong(dev.inboxbridge.dto.ImportTimelinePointView::importedMessages)
                .sum());
        assertEquals(24, stats.importTimelines().get("hour").size());
        assertEquals(30, stats.importTimelines().get("day").size());
        assertEquals(12, stats.importTimelines().get("month").size());
    }
}
