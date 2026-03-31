package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.AdminPollingSettingsView;
import dev.inboxbridge.dto.AuthSecuritySettingsView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.UpdateAdminPollingSettingsRequest;
import dev.inboxbridge.dto.UpdateAuthSecuritySettingsRequest;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.PollingService;
import dev.inboxbridge.service.PollingSettingsService;
import dev.inboxbridge.service.PollingStatsService;
import dev.inboxbridge.service.RuntimeEmailAccountService;
import dev.inboxbridge.service.SourcePollingSettingsService;
import dev.inboxbridge.service.AuthSecuritySettingsService;
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
                () -> resource.updatePollingSettings(new UpdateAdminPollingSettingsRequest(
                        Boolean.TRUE,
                        "1s",
                        Integer.valueOf(10),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)));

        assertEquals("Poll interval must be at least 5 seconds", error.getMessage());
    }

    @Test
    void emailAccountPollingSettingsReturnsSourceView() {
        AdminResource resource = new AdminResource();
        resource.sourcePollingSettingsService = new FakeSourcePollingSettingsService();

        SourcePollingSettingsView response = resource.emailAccountPollingSettings("system-fetcher");

        assertEquals("system-fetcher", response.sourceId());
        assertEquals("2m", response.effectivePollInterval());
    }

    @Test
    void runEmailAccountPollDelegatesToPollingService() {
        AdminResource resource = new AdminResource();
        resource.currentUserContext = currentUserContext();
        resource.runtimeEmailAccountService = new FakeRuntimeEmailAccountService();
        resource.pollingService = new FakeRunPollingService();

        PollRunResult response = resource.runEmailAccountPoll("system-fetcher");

        assertEquals(1, response.getFetched());
        assertEquals(1, response.getImported());
    }

    @Test
    void emailAccountPollingStatsReturnsSourceScopedView() {
        AdminResource resource = new AdminResource();
        resource.runtimeEmailAccountService = new FakeRuntimeEmailAccountService();
        resource.pollingStatsService = new FakePollingStatsService();

        SourcePollingStatsView response = resource.emailAccountPollingStats("system-fetcher");

        assertEquals(4L, response.totalImportedMessages());
        assertEquals(1, response.configuredMailFetchers());
        assertEquals(0L, response.errorPolls());
    }

    @Test
    void emailAccountPollingStatsRangeReturnsCustomTimelineBundle() {
        AdminResource resource = new AdminResource();
        resource.runtimeEmailAccountService = new FakeRuntimeEmailAccountService();
        resource.pollingStatsService = new FakePollingStatsService();

        PollingTimelineBundleView response = resource.emailAccountPollingStatsRange("system-fetcher", "2026-03-26T00:00:00Z", "2026-03-27T00:00:00Z");

        assertEquals(1, response.importTimelines().get("custom").size());
    }

    @Test
    void authSecuritySettingsReturnsCurrentView() {
        AdminResource resource = new AdminResource();
        resource.authSecuritySettingsService = new FakeAuthSecuritySettingsService();

        AuthSecuritySettingsView response = resource.authSecuritySettings();

        assertEquals(5, response.defaultLoginFailureThreshold());
        assertEquals(8, response.effectiveLoginFailureThreshold());
    }

    @Test
    void updateAuthSecuritySettingsSurfacesValidationErrors() {
        AdminResource resource = new AdminResource();
        resource.authSecuritySettingsService = new ErrorAuthSecuritySettingsService();

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.updateAuthSecuritySettings(new UpdateAuthSecuritySettingsRequest(
                        5, "PT30M", "PT10M", Boolean.TRUE, "PT10M", "ALTCHA",
                        null, null, null, null,
                        null, null, null, null, null, null, null)));

        assertEquals("Maximum login block must be greater than or equal to the initial login block", error.getMessage());
    }

    private CurrentUserContext currentUserContext() {
        CurrentUserContext context = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 1L;
        user.username = "admin";
        user.role = AppUser.Role.ADMIN;
        context.setUser(user);
        return context;
    }

    private static class FakePollingSettingsService extends PollingSettingsService {
        @Override
        public AdminPollingSettingsView view() {
            return new AdminPollingSettingsView(
                    true,
                    Boolean.TRUE,
                    true,
                    "5m",
                    "3m",
                    "3m",
                    50,
                    Integer.valueOf(25),
                    25,
                    5,
                    Integer.valueOf(4),
                    4,
                    60,
                    Integer.valueOf(90),
                    90,
                    "PT1S",
                    null,
                    "PT1S",
                    2,
                    null,
                    2,
                    "PT0.25S",
                    null,
                    "PT0.25S",
                    1,
                    null,
                    1,
                    "PT2M",
                    null,
                    "PT2M",
                    6,
                    null,
                    6,
                    0.2d,
                    null,
                    0.2d,
                    "PT30S",
                    null,
                    "PT30S");
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

    private static final class FakeRuntimeEmailAccountService extends RuntimeEmailAccountService {
        @Override
        public Optional<RuntimeEmailAccount> findSystemBridge(String sourceId) {
            return Optional.of(new RuntimeEmailAccount(
                    sourceId,
                    "SYSTEM",
                    null,
                    "system",
                    true,
                    dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP,
                    "imap.example.com",
                    993,
                    true,
                    dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.PASSWORD,
                    dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.NONE,
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
        public PollRunResult runPollForSource(RuntimeEmailAccount bridge, String trigger, String actorKey) {
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

    private static final class FakeAuthSecuritySettingsService extends AuthSecuritySettingsService {
        @Override
        public AuthSecuritySettingsView view() {
            return new AuthSecuritySettingsView(
                    5,
                    Integer.valueOf(8),
                    8,
                    "PT5M",
                    "PT10M",
                    "PT10M",
                    "PT1H",
                    "PT2H",
                    "PT2H",
                    true,
                    Boolean.FALSE,
                    false,
                    "PT10M",
                    "PT20M",
                    "PT20M",
                    "ALTCHA",
                    null,
                    "ALTCHA",
                    "ALTCHA, TURNSTILE, HCAPTCHA",
                    "",
                    null,
                    false,
                    "",
                    null,
                    false,
                    false,
                    Boolean.TRUE,
                    true,
                    "IPWHOIS",
                    "IPAPI_CO",
                    "IPAPI_CO",
                    "IPAPI_CO,IP_API,IPINFO_LITE",
                    "IP_API,IPINFO_LITE",
                    "IP_API,IPINFO_LITE",
                    "PT720H",
                    "PT240H",
                    "PT240H",
                    "PT5M",
                    "PT10M",
                    "PT10M",
                    "PT3S",
                    "PT5S",
                    "PT5S",
                    "IPWHOIS, IPAPI_CO, IP_API, IPINFO_LITE",
                    false,
                    true);
        }
    }

    private static final class ErrorAuthSecuritySettingsService extends AuthSecuritySettingsService {
        @Override
        public AuthSecuritySettingsView update(UpdateAuthSecuritySettingsRequest request) {
            throw new IllegalArgumentException("Maximum login block must be greater than or equal to the initial login block");
        }
    }

    private static final class FakePollingStatsService extends PollingStatsService {
        @Override
        public SourcePollingStatsView sourceStats(RuntimeEmailAccount bridge) {
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
        public PollingTimelineBundleView sourceTimelineBundle(RuntimeEmailAccount bridge, java.time.Instant fromInclusive, java.time.Instant toExclusive) {
            return new PollingTimelineBundleView(
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 4L))),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 1L))));
        }
    }
}
