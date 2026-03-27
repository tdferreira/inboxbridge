package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.UpdateUserPollingSettingsRequest;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.dto.UpdateUserBridgeRequest;
import dev.inboxbridge.dto.BridgeConnectionTestResult;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.dto.PollingBreakdownItemView;
import dev.inboxbridge.dto.PollingHealthSummaryView;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.UserPollingStatsView;
import dev.inboxbridge.dto.UserPollingSettingsView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.PollingService;
import dev.inboxbridge.service.RuntimeBridgeService;
import dev.inboxbridge.service.SourcePollingSettingsService;
import dev.inboxbridge.service.PollingStatsService;
import dev.inboxbridge.service.UserPollingSettingsService;
import dev.inboxbridge.service.UserBridgeService;
import jakarta.ws.rs.BadRequestException;

class UserConfigResourceTest {

    @Test
    void pollingSettingsReturnsUserView() {
        UserConfigResource resource = resource();
        resource.userPollingSettingsService = new FakeUserPollingSettingsService();

        UserPollingSettingsView response = resource.pollingSettings();

        assertEquals("3m", response.effectivePollInterval());
        assertEquals(25, response.effectiveFetchWindow());
    }

    @Test
    void pollingSettingsUpdateSurfacesValidationErrors() {
        UserConfigResource resource = resource();
        resource.userPollingSettingsService = new ErrorUserPollingSettingsService();

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.updatePollingSettings(new UpdateUserPollingSettingsRequest(Boolean.TRUE, "1s", Integer.valueOf(10))));

        assertEquals("Poll interval must be at least 5 seconds", error.getMessage());
    }

    @Test
    void bridgePollingSettingsReturnsSourceView() {
        UserConfigResource resource = resource();
        resource.sourcePollingSettingsService = new FakeSourcePollingSettingsService();

        SourcePollingSettingsView response = resource.bridgePollingSettings("fetcher-1");

        assertEquals("fetcher-1", response.sourceId());
        assertEquals("2m", response.effectivePollInterval());
    }

    @Test
    void pollingStatsReturnsUserScopedView() {
        UserConfigResource resource = resource();
        resource.pollingStatsService = new FakePollingStatsService();

        UserPollingStatsView response = resource.pollingStats();

        assertEquals(2L, response.totalImportedMessages());
        assertEquals(1, response.configuredMailFetchers());
        assertEquals(0L, response.errorPolls());
        assertEquals(1, response.importsByDay().size());
    }

    @Test
    void bridgePollingStatsReturnsSourceScopedView() {
        UserConfigResource resource = resource();
        resource.runtimeBridgeService = new FakeRuntimeBridgeService();
        resource.pollingStatsService = new FakePollingStatsService();

        SourcePollingStatsView response = resource.bridgePollingStats("fetcher-1");

        assertEquals(5L, response.totalImportedMessages());
        assertEquals(1, response.configuredMailFetchers());
        assertEquals(0L, response.errorPolls());
    }

    @Test
    void pollingStatsRangeReturnsCustomTimelineBundle() {
        UserConfigResource resource = resource();
        resource.pollingStatsService = new FakePollingStatsService();

        PollingTimelineBundleView response = resource.pollingStatsRange("2026-03-26T00:00:00Z", "2026-03-27T00:00:00Z");

        assertEquals(1, response.importTimelines().get("custom").size());
    }

    @Test
    void runBridgePollDelegatesToPollingService() {
        UserConfigResource resource = resource();
        resource.runtimeBridgeService = new FakeRuntimeBridgeService();
        resource.pollingService = new FakePollingService();

        PollRunResult response = resource.runBridgePoll("fetcher-1");

        assertEquals(1, response.getFetched());
        assertEquals(1, response.getImported());
    }

    @Test
    void testBridgeConnectionDelegatesToUserBridgeService() {
        UserConfigResource resource = resource();
        resource.userBridgeService = new FakeUserBridgeService();

        BridgeConnectionTestResult response = resource.testBridgeConnection(new UpdateUserBridgeRequest(
                null,
                "fetcher-1",
                true,
                "IMAP",
                "imap.example.com",
                993,
                true,
                "PASSWORD",
                "NONE",
                "alice@example.com",
                "Secret#123",
                "",
                "INBOX",
                false,
                "Imported/Test"));

        assertEquals(true, response.success());
        assertEquals("Connection test succeeded.", response.message());
        assertEquals("IMAP", response.protocol());
        assertEquals(Boolean.TRUE, response.folderAccessible());
    }

    private UserConfigResource resource() {
        UserConfigResource resource = new UserConfigResource();
        resource.currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";
        resource.currentUserContext.setUser(user);
        return resource;
    }

    private static final class FakeUserPollingSettingsService extends UserPollingSettingsService {
        @Override
        public Optional<UserPollingSettingsView> viewForUser(Long userId) {
            return Optional.of(defaultView(userId));
        }

        @Override
        public UserPollingSettingsView defaultView(Long userId) {
            return new UserPollingSettingsView(true, null, true, "5m", "3m", "3m", 50, Integer.valueOf(25), 25);
        }
    }

    private static final class ErrorUserPollingSettingsService extends UserPollingSettingsService {
        @Override
        public UserPollingSettingsView update(dev.inboxbridge.persistence.AppUser user, UpdateUserPollingSettingsRequest request) {
            throw new IllegalArgumentException("Poll interval must be at least 5 seconds");
        }
    }

    private static final class FakeSourcePollingSettingsService extends SourcePollingSettingsService {
        @Override
        public Optional<SourcePollingSettingsView> viewForSource(AppUser actor, String sourceId) {
            return Optional.of(new SourcePollingSettingsView(sourceId, true, Boolean.FALSE, false, "5m", "2m", "2m", 50, Integer.valueOf(20), 20));
        }

        @Override
        public SourcePollingSettingsView updateForSource(AppUser actor, String sourceId, UpdateSourcePollingSettingsRequest request) {
            return viewForSource(actor, sourceId).orElseThrow();
        }
    }

    private static final class FakePollingStatsService extends PollingStatsService {
        @Override
        public UserPollingStatsView userStats(Long userId) {
            return new UserPollingStatsView(
                    2L,
                    1,
                    1,
                    0,
                    0L,
                    java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 2L)),
                    java.util.Map.of(
                            "pastWeek", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 2L)),
                            "pastMonth", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03", 2L))),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    new PollingHealthSummaryView(1, 0, 0, 0),
                    java.util.List.of(new PollingBreakdownItemView("generic-imap", "Generic IMAP", 1L)),
                    1L,
                    0L,
                    1200L);
        }

        @Override
        public SourcePollingStatsView sourceStats(dev.inboxbridge.domain.RuntimeBridge bridge) {
            return new SourcePollingStatsView(
                    5L,
                    1,
                    1,
                    0,
                    0L,
                    java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 5L)),
                    java.util.Map.of("pastWeek", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 5L))),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    new PollingHealthSummaryView(1, 0, 0, 0),
                    java.util.List.of(new PollingBreakdownItemView("microsoft", "Microsoft", 1L)),
                    1L,
                    0L,
                    1500L);
        }

        @Override
        public PollingTimelineBundleView userTimelineBundle(Long userId, java.time.Instant fromInclusive, java.time.Instant toExclusive) {
            return new PollingTimelineBundleView(
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 2L))),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 1L))),
                    java.util.Map.of("custom", java.util.List.of()));
        }
    }

    private static final class FakeRuntimeBridgeService extends RuntimeBridgeService {
        @Override
        public Optional<dev.inboxbridge.domain.RuntimeBridge> findAccessibleForUser(AppUser actor, String sourceId) {
            return Optional.of(new dev.inboxbridge.domain.RuntimeBridge(
                    sourceId,
                    "USER",
                    actor.id,
                    actor.username,
                    true,
                    dev.inboxbridge.config.BridgeConfig.Protocol.IMAP,
                    "imap.example.com",
                    993,
                    true,
                    dev.inboxbridge.config.BridgeConfig.AuthMethod.PASSWORD,
                    dev.inboxbridge.config.BridgeConfig.OAuthProvider.NONE,
                    "alice@example.com",
                    "secret",
                    "",
                    Optional.of("INBOX"),
                    false,
                    Optional.empty(),
                    null));
        }
    }

    private static final class FakePollingService extends PollingService {
        @Override
        public PollRunResult runPollForSource(dev.inboxbridge.domain.RuntimeBridge bridge, String trigger) {
            PollRunResult result = new PollRunResult();
            result.incrementFetched();
            result.incrementImported();
            result.finish();
            return result;
        }
    }

    private static final class FakeUserBridgeService extends UserBridgeService {
        @Override
        public BridgeConnectionTestResult testConnection(AppUser user, UpdateUserBridgeRequest request) {
            return new BridgeConnectionTestResult(true, "Connection test succeeded.", "IMAP", "imap.example.com", 993, true, "PASSWORD", "NONE", true, "INBOX", true, false, Boolean.TRUE, null, 0, 0, Boolean.FALSE, null);
        }
    }
}
