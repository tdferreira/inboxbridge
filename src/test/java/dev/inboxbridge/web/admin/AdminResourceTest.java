package dev.inboxbridge.web.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.AdminPollingSettingsView;
import dev.inboxbridge.dto.AuthSecuritySettingsView;
import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.dto.PollLiveView;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.dto.SecretReencryptionResultView;
import dev.inboxbridge.dto.SecretReencryptionRequest;
import dev.inboxbridge.dto.SecretManagementStatusView;
import dev.inboxbridge.dto.SecretProviderComponentStatusView;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.dto.VerifySecretManagementPasswordRequest;
import dev.inboxbridge.dto.SourcePollingSettingsView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.UpdateAdminPollingSettingsRequest;
import dev.inboxbridge.dto.UpdateAuthSecuritySettingsRequest;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.service.polling.PollingStatsService;
import dev.inboxbridge.service.polling.PollingService;
import dev.inboxbridge.service.polling.PollingSettingsService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import dev.inboxbridge.service.polling.SourcePollingSettingsService;
import dev.inboxbridge.service.auth.AuthSecuritySettingsService;
import dev.inboxbridge.service.security.SecretManagementService;
import dev.inboxbridge.web.WebResourceSupport;
import io.smallrye.common.annotation.Blocking;
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
    void adminLivePollControlsDelegateToLiveService() {
        AdminResource resource = new AdminResource();
        resource.currentUserContext = currentUserContext();
        TrackingPollingLiveService liveService = new TrackingPollingLiveService();
        resource.pollingLiveService = liveService;

        assertEquals("run-1", resource.livePoll().runId());
        assertEquals("RUNNING", resource.pauseLivePoll().state());
        assertEquals("RUNNING", resource.resumeLivePoll().state());
        assertEquals("RUNNING", resource.stopLivePoll().state());
        assertEquals("RUNNING", resource.moveSourceNext("system-fetcher").state());
        assertEquals("RUNNING", resource.retrySource("system-fetcher").state());
        assertEquals(
                java.util.List.of("pause:admin", "resume:admin", "stop:admin", "move:admin:system-fetcher", "retry:admin:system-fetcher"),
                liveService.actions);
    }

    @Test
    void pollEventsIsBlocking() throws NoSuchMethodException {
        assertTrue(AdminResource.class.getMethod("pollEvents").isAnnotationPresent(Blocking.class));
    }

    @Test
    void emailAccountPollingStatsReturnsSourceScopedView() {
        AdminResource resource = new AdminResource();
        resource.runtimeEmailAccountService = new FakeRuntimeEmailAccountService();
        resource.pollingStatsService = new FakePollingStatsService();

        SourcePollingStatsView response = resource.emailAccountPollingStats("system-fetcher", null);

        assertEquals(4L, response.totalImportedMessages());
        assertEquals(1, response.configuredMailFetchers());
        assertEquals(0L, response.errorPolls());
    }

    @Test
    void emailAccountPollingStatsRangeReturnsCustomTimelineBundle() {
        AdminResource resource = new AdminResource();
        resource.runtimeEmailAccountService = new FakeRuntimeEmailAccountService();
        resource.pollingStatsService = new FakePollingStatsService();

        PollingTimelineBundleView response = resource.emailAccountPollingStatsRange("system-fetcher", null, "2026-03-26T00:00:00Z", "2026-03-27T00:00:00Z");

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
    void secretManagementReturnsCurrentView() {
        AdminResource resource = new AdminResource();
        resource.currentUserContext = currentUserContext();
        resource.secretManagementService = new FakeSecretManagementService();

        SecretManagementStatusView response = resource.secretManagement();

        assertTrue(response.secureStorageConfigured());
        assertEquals("LOCAL", response.mode());
        assertTrue(response.providerHealthy());
        assertTrue(response.providerWritable());
        assertEquals(1, response.providerComponents().size());
        assertEquals("LOCAL:v2", response.activeKeyVersion());
        assertEquals(2, response.protectedRecordCount());
        assertTrue(response.envManagedMailboxSecretsAllowed());
        assertEquals(1, response.configuredEnvManagedSourceCount());
        assertTrue(response.envManagedGoogleRefreshTokenConfigured());
    }

    @Test
    void reencryptStoredSecretsReturnsOperationResult() {
        AdminResource resource = new AdminResource();
        resource.currentUserContext = currentUserContext();
        resource.secretManagementService = new FakeSecretManagementService();

        SecretReencryptionResultView response = resource.reencryptStoredSecrets(new SecretReencryptionRequest(false, true, true, true));

        assertEquals("LOCAL:v2", response.activeKeyVersion());
        assertEquals(2, response.totalRecordsUpdated());
        assertEquals(4, response.followUp().browserExtensionSessionsRevoked());
    }

    @Test
    void reencryptStoredSecretsSurfacesValidationErrors() {
        AdminResource resource = new AdminResource();
        resource.currentUserContext = currentUserContext();
        resource.secretManagementService = new ErrorSecretManagementService();

        BadRequestException error = assertThrows(BadRequestException.class, () -> resource.reencryptStoredSecrets(null));

        assertEquals("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.", error.getMessage());
    }

    @Test
    void verifySecretManagementPasswordReturnsUpdatedStatus() {
        AdminResource resource = new AdminResource();
        resource.currentUserContext = currentUserContext();
        resource.secretManagementService = new FakeSecretManagementService();

        SecretManagementStatusView response = resource.verifySecretManagementPassword(new VerifySecretManagementPasswordRequest("Current1!"));

        assertTrue(response.reauthenticationRequired());
        assertTrue(response.reauthenticationSatisfied());
    }

    @Test
    void startSecretManagementPasskeyVerificationReturnsChallenge() {
        AdminResource resource = new AdminResource();
        resource.currentUserContext = currentUserContext();
        resource.secretManagementService = new FakeSecretManagementService();

        StartPasskeyCeremonyResponse response = resource.startSecretManagementPasskeyVerification();

        assertEquals("ceremony-1", response.ceremonyId());
    }

    @Test
    void finishSecretManagementPasskeyVerificationReturnsUpdatedStatus() {
        AdminResource resource = new AdminResource();
        resource.currentUserContext = currentUserContext();
        resource.secretManagementService = new FakeSecretManagementService();

        SecretManagementStatusView response = resource.finishSecretManagementPasskeyVerification(
                new FinishPasskeyCeremonyRequest("ceremony-1", "{\"id\":\"credential\"}"));

        assertTrue(response.reauthenticationRequired());
        assertTrue(response.reauthenticationSatisfied());
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
        UserSession session = new UserSession();
        session.id = 10L;
        session.userId = user.id;
        context.setSession(session);
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

    private static final class FakeSecretManagementService extends SecretManagementService {
        @Override
        public SecretManagementStatusView status(UserSession currentSession) {
            return new SecretManagementStatusView(
                    true,
                    "LOCAL",
                    "LOCAL",
                    true,
                    true,
                    "Local secret provider is ready.",
                    java.util.List.of(new SecretProviderComponentStatusView(
                            "local-key",
                            "Local inner encryption key",
                            "The local AES-GCM key path is configured and can protect InboxBridge-managed secrets.",
                            java.util.List.of("SECURITY_TOKEN_ENCRYPTION_KEY"),
                            true,
                            true)),
                    "LOCAL:v2",
                    "v2",
                    java.util.List.of("v1"),
                    2,
                    1,
                    1,
                    0,
                    true,
                    1,
                    true,
                    false,
                    java.util.List.of(),
                    true,
                    java.util.List.of(),
                    null,
                    "PT12H",
                    false,
                    true,
                    currentSession != null && currentSession.lastSensitiveAuthAt != null,
                    currentSession == null || currentSession.lastSensitiveAuthAt == null
                            ? null
                            : currentSession.lastSensitiveAuthAt.plus(java.time.Duration.ofMinutes(10)));
        }

        @Override
        public SecretReencryptionResultView reencryptAllStoredSecrets(
                dev.inboxbridge.persistence.AppUser actor,
                UserSession currentSession,
                SecretReencryptionRequest request) {
            return new SecretReencryptionResultView(
                    "COMPLETED",
                    "Secret re-encryption completed and post-run verification passed.",
                    java.time.Instant.parse("2026-04-15T00:00:00Z"),
                    "LOCAL:v2",
                    2,
                    3,
                    java.util.List.of(),
                    new dev.inboxbridge.dto.SecretReencryptionFollowUpView(
                            request != null && request.revokeBrowserExtensionSessions() ? 4 : 0,
                            request != null && request.revokeRemoteSessions() ? 3 : 0,
                            request != null && request.clearCachedOAuthAccessTokens() ? 2 : 0),
                    new dev.inboxbridge.dto.SecretReencryptionVerificationView(
                            true,
                            java.util.List.of("No stored records remain on non-active key versions."),
                            java.util.List.of("Save the active secret-management target now in use: LOCAL:v2.")));
        }

        @Override
        public SecretManagementStatusView verifyReencryptionPassword(AppUser actor, UserSession currentSession, String password) {
            currentSession.lastSensitiveAuthAt = java.time.Instant.parse("2026-04-15T00:00:00Z");
            return status(currentSession);
        }

        @Override
        public StartPasskeyCeremonyResponse startReencryptionPasskeyVerification(AppUser actor, UserSession currentSession) {
            return new StartPasskeyCeremonyResponse("ceremony-1", "{\"challenge\":\"abc\"}");
        }

        @Override
        public SecretManagementStatusView finishReencryptionPasskeyVerification(
                AppUser actor,
                UserSession currentSession,
                FinishPasskeyCeremonyRequest request) {
            currentSession.lastSensitiveAuthAt = java.time.Instant.parse("2026-04-15T00:00:00Z");
            return status(currentSession);
        }
    }

    private static final class ErrorSecretManagementService extends SecretManagementService {
        @Override
        public SecretReencryptionResultView reencryptAllStoredSecrets(
                dev.inboxbridge.persistence.AppUser actor,
                UserSession currentSession,
                SecretReencryptionRequest request) {
            throw new IllegalStateException("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.");
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
        public PollRunResult runPollForSource(RuntimeEmailAccount bridge, String trigger, AppUser actor, String actorKey) {
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
        public SourcePollingStatsView sourceStats(RuntimeEmailAccount bridge, java.time.ZoneId zoneId) {
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
                    java.util.Map.of(),
                    new dev.inboxbridge.dto.PollingHealthSummaryView(1, 0, 0, 0),
                    java.util.List.of(new dev.inboxbridge.dto.PollingBreakdownItemView("generic-imap", "Generic IMAP", 1L)),
                    0L,
                    1L,
                    0L,
                    900L);
        }

        @Override
        public PollingTimelineBundleView sourceTimelineBundle(RuntimeEmailAccount bridge, java.time.Instant fromInclusive, java.time.Instant toExclusive, java.time.ZoneId zoneId) {
            return new PollingTimelineBundleView(
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 4L))),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of()),
                    java.util.Map.of("custom", java.util.List.of(new dev.inboxbridge.dto.ImportTimelinePointView("2026-03-26", 1L))),
                    java.util.Map.of("custom", java.util.List.of()));
        }
    }

    private static final class TrackingPollingLiveService extends dev.inboxbridge.service.polling.PollingLiveService {
        private final java.util.List<String> actions = new java.util.ArrayList<>();

        @Override
        public PollLiveView snapshotFor(AppUser viewer) {
            return new PollLiveView(true, "run-1", "RUNNING", "admin-ui", "admin", true, "system-fetcher", null, null, java.util.List.of());
        }

        @Override
        public boolean requestPause(AppUser actor) {
            actions.add("pause:" + actor.username);
            return true;
        }

        @Override
        public boolean requestResume(AppUser actor) {
            actions.add("resume:" + actor.username);
            return true;
        }

        @Override
        public boolean requestStop(AppUser actor) {
            actions.add("stop:" + actor.username);
            return true;
        }

        @Override
        public boolean moveSourceToFront(AppUser actor, String sourceId) {
            actions.add("move:" + actor.username + ":" + sourceId);
            return true;
        }

        @Override
        public boolean retrySource(AppUser actor, String sourceId) {
            actions.add("retry:" + actor.username + ":" + sourceId);
            return true;
        }
    }
}
