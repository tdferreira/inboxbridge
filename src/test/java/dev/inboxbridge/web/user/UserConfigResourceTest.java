package dev.inboxbridge.web.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.DestinationMailboxFolderOptionsView;
import dev.inboxbridge.dto.UpdateUserPollingSettingsRequest;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.dto.UpdateUserEmailAccountRequest;
import dev.inboxbridge.dto.UpdateUserMailDestinationRequest;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
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
import dev.inboxbridge.service.polling.PollingService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import dev.inboxbridge.service.SourcePollingSettingsService;
import dev.inboxbridge.service.polling.PollingStatsService;
import dev.inboxbridge.service.user.UserPollingSettingsService;
import dev.inboxbridge.service.user.UserEmailAccountService;
import dev.inboxbridge.service.user.UserMailDestinationConfigService;
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
    void emailAccountPollingSettingsReturnsSourceView() {
        UserConfigResource resource = resource();
        resource.sourcePollingSettingsService = new FakeSourcePollingSettingsService();

        SourcePollingSettingsView response = resource.emailAccountPollingSettings("fetcher-1");

        assertEquals("fetcher-1", response.sourceId());
        assertEquals("2m", response.effectivePollInterval());
    }

    @Test
    void pollingStatsReturnsUserScopedView() {
        UserConfigResource resource = resource();
        resource.pollingStatsService = new FakePollingStatsService();

        UserPollingStatsView response = resource.pollingStats(null);

        assertEquals(2L, response.totalImportedMessages());
        assertEquals(1, response.configuredMailFetchers());
        assertEquals(0L, response.errorPolls());
        assertEquals(1, response.importsByDay().size());
    }

    @Test
    void emailAccountPollingStatsReturnsSourceScopedView() {
        UserConfigResource resource = resource();
        resource.runtimeEmailAccountService = new FakeRuntimeEmailAccountService();
        resource.pollingStatsService = new FakePollingStatsService();

        SourcePollingStatsView response = resource.emailAccountPollingStats("fetcher-1", null);

        assertEquals(5L, response.totalImportedMessages());
        assertEquals(1, response.configuredMailFetchers());
        assertEquals(0L, response.errorPolls());
    }

    @Test
    void pollingStatsRangeReturnsCustomTimelineBundle() {
        UserConfigResource resource = resource();
        resource.pollingStatsService = new FakePollingStatsService();

        PollingTimelineBundleView response = resource.pollingStatsRange(null, "2026-03-26T00:00:00Z", "2026-03-27T00:00:00Z");

        assertEquals(1, response.importTimelines().get("custom").size());
    }

    @Test
    void runEmailAccountPollDelegatesToPollingService() {
        UserConfigResource resource = resource();
        resource.runtimeEmailAccountService = new FakeRuntimeEmailAccountService();
        resource.pollingService = new FakePollingService();

        PollRunResult response = resource.runEmailAccountPoll("fetcher-1");

        assertEquals(1, response.getFetched());
        assertEquals(1, response.getImported());
    }

    @Test
    void runUserPollDelegatesToPollingService() {
        UserConfigResource resource = resource();
        resource.pollingService = new FakePollingService();

        PollRunResult response = resource.runUserPoll();

        assertEquals(1, response.getFetched());
        assertEquals(1, response.getImported());
    }

    @Test
    void testEmailAccountConnectionDelegatesToUserEmailAccountService() {
        UserConfigResource resource = resource();
        resource.userEmailAccountService = new FakeUserEmailAccountService();

        EmailAccountConnectionTestResult response = resource.testEmailAccountConnection(new UpdateUserEmailAccountRequest(
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

    @Test
    void emailAccountFoldersDelegatesToUserEmailAccountService() {
        UserConfigResource resource = resource();
        resource.userEmailAccountService = new FakeUserEmailAccountService();

        DestinationMailboxFolderOptionsView response = resource.emailAccountFolders(new UpdateUserEmailAccountRequest(
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

        assertEquals(List.of("INBOX", "Archive"), response.folders());
    }

    @Test
    void destinationFoldersReturnsUserScopedFolderOptions() {
        UserConfigResource resource = resource();
        resource.userMailDestinationConfigService = new FakeUserMailDestinationConfigService();

        DestinationMailboxFolderOptionsView response = resource.destinationFolders();

        assertEquals(List.of("INBOX", "Archive"), response.folders());
    }

    @Test
    void destinationFoldersMapsValidationErrorsToBadRequest() {
        UserConfigResource resource = resource();
        resource.userMailDestinationConfigService = new ErrorUserMailDestinationConfigService();

        BadRequestException error = assertThrows(BadRequestException.class, resource::destinationFolders);

        assertEquals("Save and connect the destination mailbox before loading folders.", error.getMessage());
    }

    @Test
    void testDestinationConnectionDelegatesToDestinationService() {
        UserConfigResource resource = resource();
        resource.userMailDestinationConfigService = new FakeUserMailDestinationConfigService();

        EmailAccountConnectionTestResult response = resource.testDestinationConnection(new UpdateUserMailDestinationRequest(
                "OUTLOOK_IMAP",
                "outlook.office365.com",
                993,
                true,
                "OAUTH2",
                "MICROSOFT",
                "alice@example.com",
                "",
                "INBOX"));

        assertEquals(true, response.success());
        assertEquals("Connection test succeeded.", response.message());
    }

    @Test
    void testDestinationConnectionMapsValidationErrorsToBadRequest() {
        UserConfigResource resource = resource();
        resource.userMailDestinationConfigService = new ErrorUserMailDestinationConfigService() {
            @Override
            public EmailAccountConnectionTestResult testConnectionForUser(AppUser user, UpdateUserMailDestinationRequest request) {
                throw new IllegalStateException("Save and connect the destination mailbox before testing it.");
            }
        };

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> resource.testDestinationConnection(new UpdateUserMailDestinationRequest(
                        "OUTLOOK_IMAP",
                        "outlook.office365.com",
                        993,
                        true,
                        "OAUTH2",
                        "MICROSOFT",
                        "alice@example.com",
                        "",
                        "INBOX")));

        assertEquals("Save and connect the destination mailbox before testing it.", error.getMessage());
    }

    private UserConfigResource resource() {
        UserConfigResource resource = new UserConfigResource();
        resource.currentUserContext = new CurrentUserContext();
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";
        user.role = AppUser.Role.USER;
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
        public UserPollingStatsView userStats(Long userId, java.time.ZoneId zoneId) {
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
                    java.util.Map.of(),
                    new PollingHealthSummaryView(1, 0, 0, 0),
                    java.util.List.of(new PollingBreakdownItemView("generic-imap", "Generic IMAP", 1L)),
                    1L,
                    0L,
                    0L,
                    1200L);
        }

        @Override
        public SourcePollingStatsView sourceStats(dev.inboxbridge.domain.RuntimeEmailAccount bridge, java.time.ZoneId zoneId) {
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
                    java.util.Map.of(),
                    new PollingHealthSummaryView(1, 0, 0, 0),
                    java.util.List.of(new PollingBreakdownItemView("microsoft", "Microsoft", 1L)),
                    1L,
                    0L,
                    0L,
                    1500L);
        }

        @Override
        public PollingTimelineBundleView userTimelineBundle(Long userId, java.time.Instant fromInclusive, java.time.Instant toExclusive, java.time.ZoneId zoneId) {
            return new PollingTimelineBundleView(
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 2L))),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 1L))),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of()));
        }
    }

    private static final class FakePollingService extends PollingService {
        @Override
        public PollRunResult runPollForSource(dev.inboxbridge.domain.RuntimeEmailAccount bridge, String trigger, AppUser actor, String actorKey) {
            PollRunResult result = new PollRunResult();
            result.incrementFetched();
            result.incrementImported();
            result.finish();
            return result;
        }

        @Override
        public PollRunResult runPollForUser(AppUser actor, String trigger) {
            PollRunResult result = new PollRunResult();
            result.incrementFetched();
            result.incrementImported();
            result.finish();
            return result;
        }
    }

    private static final class FakeRuntimeEmailAccountService extends RuntimeEmailAccountService {
        @Override
        public Optional<dev.inboxbridge.domain.RuntimeEmailAccount> findAccessibleForUser(AppUser actor, String sourceId) {
            return Optional.of(new dev.inboxbridge.domain.RuntimeEmailAccount(
                    sourceId,
                    "USER",
                    actor.id,
                    actor.username,
                    true,
                    dev.inboxbridge.config.InboxBridgeConfig.Protocol.IMAP,
                    "imap.example.com",
                    993,
                    true,
                    dev.inboxbridge.config.InboxBridgeConfig.AuthMethod.PASSWORD,
                    dev.inboxbridge.config.InboxBridgeConfig.OAuthProvider.NONE,
                    "alice@example.com",
                    "secret",
                    "",
                    Optional.of("INBOX"),
                    false,
                    Optional.empty(),
                    null));
        }
    }

    private static final class FakeUserEmailAccountService extends UserEmailAccountService {
        @Override
        public DestinationMailboxFolderOptionsView listFolders(AppUser user, UpdateUserEmailAccountRequest request) {
            return new DestinationMailboxFolderOptionsView(List.of("INBOX", "Archive"));
        }

        @Override
        public EmailAccountConnectionTestResult testConnection(AppUser user, UpdateUserEmailAccountRequest request) {
            return new EmailAccountConnectionTestResult(true, "Connection test succeeded.", "IMAP", "imap.example.com", 993, true, "PASSWORD", "NONE", true, "INBOX", true, false, Boolean.TRUE, null, 0, 0, Boolean.FALSE, null, Boolean.TRUE);
        }
    }

    private static final class FakeUserMailDestinationConfigService extends UserMailDestinationConfigService {
        @Override
        public DestinationMailboxFolderOptionsView listFoldersForUser(Long userId, String ownerUsername) {
            return new DestinationMailboxFolderOptionsView(List.of("INBOX", "Archive"));
        }

        @Override
        public EmailAccountConnectionTestResult testConnectionForUser(AppUser user, UpdateUserMailDestinationRequest request) {
            return new EmailAccountConnectionTestResult(true, "Connection test succeeded.", "IMAP", request.host(), request.port(), request.tls(), request.authMethod(), request.oauthProvider(), true, request.folder(), true, false, null, null, 0, null, false, null, null);
        }
    }

    private static class ErrorUserMailDestinationConfigService extends UserMailDestinationConfigService {
        @Override
        public DestinationMailboxFolderOptionsView listFoldersForUser(Long userId, String ownerUsername) {
            throw new IllegalStateException("Save and connect the destination mailbox before loading folders.");
        }
    }
}
