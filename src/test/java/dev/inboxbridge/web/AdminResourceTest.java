package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.AdminPollingSettingsView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.UpdateAdminPollingSettingsRequest;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.service.PollingService;
import dev.inboxbridge.service.PollingSettingsService;
import dev.inboxbridge.service.PollingStatsService;
import dev.inboxbridge.service.RuntimeBridgeService;
import dev.inboxbridge.service.SourcePollingSettingsService;
import jakarta.ws.rs.BadRequestException;

class AdminResourceTest {

    @Test
    void pollingSettingsReturnsCurrentView() {
        AdminResource resource = new AdminResource();
        resource.pollingSettingsService = new FakePollingSettingsService();

        AdminPollingSettingsView response = resource.pollingSettings();

        assertEquals("5m", response.defaultPollInterval());
        assertEquals("3m", response.effectivePollInterval());
    }

    @Test
    void updatePollingSettingsSurfacesValidationErrors() {
        AdminResource resource = new AdminResource();
        resource.pollingSettingsService = new ErrorPollingSettingsService();

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.updatePollingSettings(new UpdateAdminPollingSettingsRequest(Boolean.TRUE, "1s", Integer.valueOf(10))));

        assertEquals("Poll interval must be at least 5 seconds", error.getMessage());
    }

    @Test
    void bridgePollingSettingsReturnsSourceView() {
        AdminResource resource = new AdminResource();
        resource.sourcePollingSettingsService = new FakeSourcePollingSettingsService();

        SourcePollingSettingsView response = resource.bridgePollingSettings("system-fetcher");

        assertEquals("system-fetcher", response.sourceId());
        assertEquals("2m", response.effectivePollInterval());
    }

    @Test
    void runBridgePollDelegatesToPollingService() {
        AdminResource resource = new AdminResource();
        resource.runtimeBridgeService = new FakeRuntimeBridgeService();
        resource.pollingService = new FakeRunPollingService();

        PollRunResult response = resource.runBridgePoll("system-fetcher");

        assertEquals(1, response.getFetched());
        assertEquals(1, response.getImported());
    }

    @Test
    void bridgePollingStatsReturnsSourceScopedView() {
        AdminResource resource = new AdminResource();
        resource.runtimeBridgeService = new FakeRuntimeBridgeService();
        resource.pollingStatsService = new FakePollingStatsService();

        SourcePollingStatsView response = resource.bridgePollingStats("system-fetcher");

        assertEquals(4L, response.totalImportedMessages());
        assertEquals(1, response.configuredMailFetchers());
        assertEquals(0L, response.errorPolls());
    }

    @Test
    void bridgePollingStatsRangeReturnsCustomTimelineBundle() {
        AdminResource resource = new AdminResource();
        resource.runtimeBridgeService = new FakeRuntimeBridgeService();
        resource.pollingStatsService = new FakePollingStatsService();

        PollingTimelineBundleView response = resource.bridgePollingStatsRange("system-fetcher", "2026-03-26T00:00:00Z", "2026-03-27T00:00:00Z");

        assertEquals(1, response.importTimelines().get("custom").size());
    }

    private static class FakePollingSettingsService extends PollingSettingsService {
        @Override
        public AdminPollingSettingsView view() {
            return new AdminPollingSettingsView(true, Boolean.TRUE, true, "5m", "3m", "3m", 50, Integer.valueOf(25), 25);
        }
    }

    private static final class FakeSourcePollingSettingsService extends SourcePollingSettingsService {
        @Override
        public Optional<SourcePollingSettingsView> viewForSystemSource(String sourceId) {
            return Optional.of(new SourcePollingSettingsView(sourceId, true, Boolean.FALSE, false, "5m", "2m", "2m", 50, Integer.valueOf(20), 20));
        }

        @Override
        public SourcePollingSettingsView updateForSystemSource(String sourceId, UpdateSourcePollingSettingsRequest request) {
            return viewForSystemSource(sourceId).orElseThrow();
        }
    }

    private static final class FakeRuntimeBridgeService extends RuntimeBridgeService {
        @Override
        public Optional<RuntimeBridge> findSystemBridge(String sourceId) {
            return Optional.of(new RuntimeBridge(
                    sourceId,
                    "SYSTEM",
                    null,
                    "system",
                    true,
                    dev.inboxbridge.config.BridgeConfig.Protocol.IMAP,
                    "imap.example.com",
                    993,
                    true,
                    dev.inboxbridge.config.BridgeConfig.AuthMethod.PASSWORD,
                    dev.inboxbridge.config.BridgeConfig.OAuthProvider.NONE,
                    "admin@example.com",
                    "secret",
                    "",
                    Optional.of("INBOX"),
                    false,
                    Optional.empty(),
                    null));
        }
    }

    private static final class FakeRunPollingService extends PollingService {
        @Override
        public PollRunResult runPollForSource(RuntimeBridge bridge, String trigger) {
            PollRunResult result = new PollRunResult();
            result.incrementFetched();
            result.incrementImported();
            result.finish();
            return result;
        }
    }

    private static final class ErrorPollingSettingsService extends PollingSettingsService {
        @Override
        public AdminPollingSettingsView update(UpdateAdminPollingSettingsRequest request) {
            throw new IllegalArgumentException("Poll interval must be at least 5 seconds");
        }
    }

    private static final class FakePollingStatsService extends PollingStatsService {
        @Override
        public SourcePollingStatsView sourceStats(RuntimeBridge bridge) {
            return new SourcePollingStatsView(
                    4L,
                    1,
                    1,
                    0,
                    0L,
                    java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 4L)),
                    java.util.Map.of("pastWeek", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 4L))),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    java.util.Map.of(),
                    new dev.inboxbridge.dto.PollingHealthSummaryView(1, 0, 0, 0),
                    java.util.List.of(new dev.inboxbridge.dto.PollingBreakdownItemView("generic-imap", "Generic IMAP", 1L)),
                    0L,
                    1L,
                    900L);
        }

        @Override
        public PollingTimelineBundleView sourceTimelineBundle(RuntimeBridge bridge, java.time.Instant fromInclusive, java.time.Instant toExclusive) {
            return new PollingTimelineBundleView(
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 4L))),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 1L))));
        }
    }
}
