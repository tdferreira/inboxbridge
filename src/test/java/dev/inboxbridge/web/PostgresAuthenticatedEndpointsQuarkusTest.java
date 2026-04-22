package dev.inboxbridge.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollAction;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.ExtensionSessionRepository;
import dev.inboxbridge.persistence.ImportedMessage;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.persistence.RemoteSessionRepository;
import dev.inboxbridge.persistence.SourceImapCheckpoint;
import dev.inboxbridge.persistence.SourceImapCheckpointRepository;
import dev.inboxbridge.persistence.SourcePollEvent;
import dev.inboxbridge.persistence.SourcePollEventRepository;
import dev.inboxbridge.persistence.SourcePollingSetting;
import dev.inboxbridge.persistence.SourcePollingSettingRepository;
import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.persistence.SourcePollingStateRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import dev.inboxbridge.persistence.UserPasskey;
import dev.inboxbridge.persistence.UserPasskeyRepository;
import dev.inboxbridge.persistence.UserPollingSetting;
import dev.inboxbridge.persistence.UserPollingSettingRepository;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.persistence.UserSessionRepository;
import dev.inboxbridge.persistence.UserUiPreference;
import dev.inboxbridge.persistence.UserUiPreferenceRepository;
import dev.inboxbridge.service.admin.AppUserService;
import dev.inboxbridge.service.security.SecretEncryptionService;
import dev.inboxbridge.testsupport.PostgresQuarkusTestProfile;
import dev.inboxbridge.testsupport.PostgresTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;

/**
 * Exercises the authenticated HTTP surfaces against PostgreSQL-backed Quarkus
 * augmentation so login, session cookies, bearer-token rotation, UI
 * preferences, and admin security endpoints are verified with the same
 * persistence engine and Flyway migrations used in production.
 */
@QuarkusTest
@TestProfile(PostgresQuarkusTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
class PostgresAuthenticatedEndpointsQuarkusTest {

    private static final String SESSION_COOKIE = "inboxbridge_session";
    private static final String CSRF_COOKIE = "inboxbridge_csrf";
    private static final String REMOTE_SESSION_COOKIE = "inboxbridge_remote_session";
    private static final String REMOTE_CSRF_COOKIE = "inboxbridge_remote_csrf";
    private static final String CSRF_HEADER = "X-InboxBridge-CSRF";

    @Inject
    AppUserService appUserService;

    @Inject
    SecretEncryptionService secretEncryptionService;

    @Inject
    UserUiPreferenceRepository userUiPreferenceRepository;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    UserMailDestinationConfigRepository userMailDestinationConfigRepository;

    @Inject
    UserGmailConfigRepository userGmailConfigRepository;

    @Inject
    UserPollingSettingRepository userPollingSettingRepository;

    @Inject
    UserPasskeyRepository userPasskeyRepository;

    @Inject
    UserSessionRepository userSessionRepository;

    @Inject
    ExtensionSessionRepository extensionSessionRepository;

    @Inject
    RemoteSessionRepository remoteSessionRepository;

    @Inject
    SourcePollingSettingRepository sourcePollingSettingRepository;

    @Inject
    SourcePollingStateRepository sourcePollingStateRepository;

    @Inject
    SourceImapCheckpointRepository sourceImapCheckpointRepository;

    @Inject
    SourcePollEventRepository sourcePollEventRepository;

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    OAuthCredentialRepository oAuthCredentialRepository;

    @Test
    void browserSessionCanPersistUiPreferencesThroughTheRealHttpSurface() {
        BrowserSession session = loginBrowserAsAdmin();

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .cookie(CSRF_COOKIE, session.csrfToken())
                .header(CSRF_HEADER, session.csrfToken())
                .contentType("application/json")
                .body(Map.of(
                        "persistLayout", true,
                        "layoutEditEnabled", true,
                        "language", "pt-PT",
                        "themeMode", "DARK_BLUE",
                        "dateFormat", "YYYY-MM-DD HH:mm",
                        "timezoneMode", "MANUAL",
                        "timezone", "Europe/Lisbon",
                        "userSectionOrder", List.of("destination", "sourceEmailAccounts"),
                        "adminSectionOrder", List.of("authSecurity", "globalStats"),
                        "notificationHistory", List.of(Map.of(
                                "id", "notif-endpoint-1",
                                "message", Map.of("key", "notifications.syncFailed"),
                                "details", Map.of("text", "Sync failed from endpoint test"),
                                "severity", "error",
                                "sourceId", "source-endpoint",
                                "groupKey", "poll-errors",
                                "createdAt", 1_711_200_000L,
                                "read", false))))
                .when().put("/api/app/ui-preferences")
                .then()
                .statusCode(200)
                .body("persistLayout", equalTo(true))
                .body("language", equalTo("pt-PT"))
                .body("themeMode", equalTo("DARK_BLUE"))
                .body("timezone", equalTo("Europe/Lisbon"))
                .body("notificationHistory.size()", equalTo(1));

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/app/ui-preferences")
                .then()
                .statusCode(200)
                .body("persistLayout", equalTo(true))
                .body("language", equalTo("pt-PT"))
                .body("themeMode", equalTo("DARK_BLUE"))
                .body("dateFormat", equalTo("YYYY-MM-DD HH:mm"))
                .body("timezoneMode", equalTo("MANUAL"))
                .body("timezone", equalTo("Europe/Lisbon"))
                .body("notificationHistory[0].id", equalTo("notif-endpoint-1"));

        UserUiPreference stored = userUiPreferenceRepository.findByUserId(adminUser().id).orElseThrow();
        assertTrue(stored.persistLayout);
        assertEquals("pt-PT", stored.language);
        assertEquals("DARK_BLUE", stored.themeMode);
        assertEquals("Europe/Lisbon", stored.timezone);
        assertTrue(stored.notificationHistory.contains("notif-endpoint-1"));
    }

    @Test
    void extensionAndBrowserSessionEndpointsShareThePersistedPostgresState() {
        BrowserSession browserSession = loginBrowserAsAdmin();

        given()
                .cookie(SESSION_COOKIE, browserSession.sessionToken())
                .cookie(CSRF_COOKIE, browserSession.csrfToken())
                .header(CSRF_HEADER, browserSession.csrfToken())
                .contentType("application/json")
                .body(Map.of(
                        "language", "fr-FR",
                        "themeMode", "LIGHT_BLUE"))
                .when().put("/api/app/ui-preferences")
                .then()
                .statusCode(200)
                .body("language", equalTo("fr-FR"))
                .body("themeMode", equalTo("LIGHT_BLUE"));

        ExtensionAuthSession extensionSession = loginExtensionAsAdmin("QA laptop", "firefox", "0.7.0");

        given()
                .header("Authorization", "Bearer " + extensionSession.accessToken())
                .when().get("/api/extension/status")
                .then()
                .statusCode(200)
                .body("user.username", equalTo("admin"))
                .body("user.language", equalTo("fr-FR"))
                .body("user.themeMode", equalTo("LIGHT_BLUE"))
                .body("poll.canRun", equalTo(true))
                .body("summary.sourceCount", notNullValue());

        given()
                .cookie(SESSION_COOKIE, browserSession.sessionToken())
                .when().get("/api/extension/sessions")
                .then()
                .statusCode(200)
                .body("find { it.label == 'QA laptop' }.browserFamily", equalTo("firefox"))
                .body("find { it.label == 'QA laptop' }.extensionVersion", equalTo("0.7.0"));

        ValidatableResponse refreshResponse = given()
                .contentType("application/json")
                .body(Map.of("refreshToken", extensionSession.refreshToken()))
                .when().post("/api/extension/auth/refresh")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTHENTICATED"))
                .body("session.tokens.accessToken", notNullValue())
                .body("session.tokens.refreshToken", notNullValue());

        String refreshedAccessToken = refreshResponse.extract().path("session.tokens.accessToken");
        String refreshedRefreshToken = refreshResponse.extract().path("session.tokens.refreshToken");

        given()
                .cookie(SESSION_COOKIE, browserSession.sessionToken())
                .cookie(CSRF_COOKIE, browserSession.csrfToken())
                .header(CSRF_HEADER, browserSession.csrfToken())
                .when().delete("/api/extension/sessions")
                .then()
                .statusCode(204);

        given()
                .header("Authorization", "Bearer " + refreshedAccessToken)
                .when().get("/api/extension/status")
                .then()
                .statusCode(401);

        List<ExtensionSession> sessions = extensionSessionRepository.listByUserId(adminUser().id);
        assertNotNull(sessions.stream()
                .filter((session) -> "QA laptop".equals(session.label))
                .findFirst()
                .orElseThrow()
                .revokedAt);
        assertFalse(refreshedRefreshToken.isBlank());
    }

    @Test
    void extensionBrowserHandoffCompletesAndRedeemsAgainstRealBrowserSessionState() {
        BrowserSession browserSession = loginBrowserAsAdmin();
        String codeVerifier = "pgtest-browser-handoff-verifier";
        String codeChallenge = pkceS256(codeVerifier);

        ValidatableResponse startResponse = given()
                .contentType("application/json")
                .body(Map.of(
                        "codeChallenge", codeChallenge,
                        "codeChallengeMethod", "S256",
                        "label", "Browser handoff session",
                        "browserFamily", "chromium",
                        "extensionVersion", "1.2.3"))
                .when().post("/api/extension/auth/browser-handoff/start")
                .then()
                .statusCode(200)
                .body("requestId", notNullValue())
                .body("browserUrl", notNullValue())
                .body("expiresAt", notNullValue());

        String requestId = startResponse.extract().path("requestId");

        given()
                .contentType("application/json")
                .body(Map.of(
                        "requestId", requestId,
                        "codeVerifier", codeVerifier))
                .when().post("/api/extension/auth/browser-handoff/redeem")
                .then()
                .statusCode(200)
                .body("status", equalTo("PENDING"))
                .body("session", equalTo(null));

        given()
                .cookie(SESSION_COOKIE, browserSession.sessionToken())
                .cookie(CSRF_COOKIE, browserSession.csrfToken())
                .header(CSRF_HEADER, browserSession.csrfToken())
                .contentType("application/json")
                .body(Map.of("requestId", requestId))
                .when().post("/api/extension/auth/browser-handoff/complete")
                .then()
                .statusCode(200)
                .body("status", equalTo("COMPLETED"));

        ValidatableResponse redeemResponse = given()
                .contentType("application/json")
                .body(Map.of(
                        "requestId", requestId,
                        "codeVerifier", codeVerifier))
                .when().post("/api/extension/auth/browser-handoff/redeem")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTHENTICATED"))
                .body("session.label", equalTo("Browser handoff session"))
                .body("session.browserFamily", equalTo("chromium"))
                .body("session.extensionVersion", equalTo("1.2.3"))
                .body("session.tokens.accessToken", notNullValue())
                .body("session.tokens.refreshToken", notNullValue());

        String accessToken = redeemResponse.extract().path("session.tokens.accessToken");

        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().get("/api/extension/status")
                .then()
                .statusCode(200)
                .body("user.username", equalTo("admin"));

        given()
                .cookie(SESSION_COOKIE, browserSession.sessionToken())
                .when().get("/api/extension/sessions")
                .then()
                .statusCode(200)
                .body("find { it.label == 'Browser handoff session' }.browserFamily", equalTo("chromium"))
                .body("find { it.label == 'Browser handoff session' }.extensionVersion", equalTo("1.2.3"));

        given()
                .contentType("application/json")
                .body(Map.of(
                        "requestId", requestId,
                        "codeVerifier", codeVerifier))
                .when().post("/api/extension/auth/browser-handoff/redeem")
                .then()
                .statusCode(200)
                .body("status", equalTo("EXPIRED"))
                .body("session", equalTo(null));
    }

    @Test
    void remoteSessionEndpointsPersistSessionMetadataOnPostgres() {
        BrowserSession browserSession = loginBrowserAsAdmin();

        given()
                .cookie(SESSION_COOKIE, browserSession.sessionToken())
                .cookie(CSRF_COOKIE, browserSession.csrfToken())
                .header(CSRF_HEADER, browserSession.csrfToken())
                .contentType("application/json")
                .body(Map.of(
                        "language", "es-ES",
                        "themeMode", "DARK_GREEN",
                        "dateFormat", "DMY_24",
                        "timezoneMode", "MANUAL",
                        "timezone", "Europe/Madrid"))
                .when().put("/api/app/ui-preferences")
                .then()
                .statusCode(200)
                .body("language", equalTo("es-ES"))
                .body("themeMode", equalTo("DARK_GREEN"));

        RemoteBrowserSession remoteSession = loginRemoteAsAdmin();

        given()
                .cookie(REMOTE_SESSION_COOKIE, remoteSession.sessionToken())
                .when().get("/api/remote/auth/me")
                .then()
                .statusCode(200)
                .body("username", equalTo("admin"))
                .body("language", equalTo("es-ES"))
                .body("themeMode", equalTo("DARK_GREEN"))
                .body("timezoneMode", equalTo("MANUAL"))
                .body("timezone", equalTo("Europe/Madrid"))
                .body("deviceLocationCaptured", equalTo(false));

        given()
                .cookie(REMOTE_SESSION_COOKIE, remoteSession.sessionToken())
                .cookie(REMOTE_CSRF_COOKIE, remoteSession.csrfToken())
                .header(CSRF_HEADER, remoteSession.csrfToken())
                .contentType("application/json")
                .body(Map.of(
                        "latitude", 38.7223,
                        "longitude", -9.1393,
                        "accuracyMeters", 25.0))
                .when().post("/api/remote/auth/session/device-location")
                .then()
                .statusCode(204);

        given()
                .cookie(REMOTE_SESSION_COOKIE, remoteSession.sessionToken())
                .when().get("/api/remote/auth/me")
                .then()
                .statusCode(200)
                .body("deviceLocationCaptured", equalTo(true));

        List<RemoteSession> sessions = remoteSessionRepository.listRecentByUserId(adminUser().id, 10);
        assertFalse(sessions.isEmpty());
        assertNotNull(sessions.getFirst().deviceLocationCapturedAt);
        assertNotNull(sessions.getFirst().deviceLatitude);
        assertNotNull(sessions.getFirst().deviceLongitude);
    }

    @Test
    void destinationAndEmailAccountEndpointsReadPersistedPostgresConfiguration() {
        ConfiguredMailboxUserFixture fixture = QuarkusTransaction.requiringNew().call(this::seedConfiguredMailboxUser);
        BrowserSession session = loginBrowser(fixture.username(), fixture.password());

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/app/destination-config")
                .then()
                .statusCode(200)
                .body("configured", equalTo(true))
                .body("provider", equalTo("GENERIC_IMAP"))
                .body("host", equalTo("imap.destination-config.test"))
                .body("port", equalTo(993))
                .body("tls", equalTo(true))
                .body("authMethod", equalTo("PASSWORD"))
                .body("username", equalTo("destination-config@example.com"))
                .body("folder", equalTo("Archive/InboxBridge"));

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/app/email-accounts")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].emailAccountId", equalTo(fixture.sourceId()))
                .body("[0].enabled", equalTo(true))
                .body("[0].protocol", equalTo("IMAP"))
                .body("[0].host", equalTo("imap.source-config.test"))
                .body("[0].port", equalTo(993))
                .body("[0].tls", equalTo(true))
                .body("[0].authMethod", equalTo("PASSWORD"))
                .body("[0].username", equalTo("source-config@example.com"))
                .body("[0].folder", equalTo("Projects/InboxBridge"))
                .body("[0].customLabel", equalTo("Config endpoint source"))
                .body("[0].fetchMode", equalTo("POLLING"))
                .body("[0].postPollAction", equalTo("MOVE"))
                .body("[0].postPollTargetFolder", equalTo("Processed"))
                .body("[0].passwordConfigured", equalTo(true))
                .body("[0].oauthRefreshTokenConfigured", equalTo(false));
    }

    @Test
    void pollingSettingsEndpointsReadPersistedUserAndSourceOverrides() {
        ConfiguredMailboxUserFixture fixture = QuarkusTransaction.requiringNew().call(this::seedConfiguredMailboxUser);
        BrowserSession session = loginBrowser(fixture.username(), fixture.password());

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/app/polling-settings")
                .then()
                .statusCode(200)
                .body("pollEnabledOverride", equalTo(false))
                .body("effectivePollEnabled", equalTo(false))
                .body("pollIntervalOverride", equalTo("PT20M"))
                .body("effectivePollInterval", equalTo("PT20M"))
                .body("fetchWindowOverride", equalTo(30))
                .body("effectiveFetchWindow", equalTo(30));

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/app/email-accounts/{emailAccountId}/polling-settings", fixture.sourceId())
                .then()
                .statusCode(200)
                .body("sourceId", equalTo(fixture.sourceId()))
                .body("basePollEnabled", equalTo(false))
                .body("pollEnabledOverride", equalTo(true))
                .body("effectivePollEnabled", equalTo(true))
                .body("basePollInterval", equalTo("PT20M"))
                .body("pollIntervalOverride", equalTo("PT5M"))
                .body("effectivePollInterval", equalTo("PT5M"))
                .body("baseFetchWindow", equalTo(30))
                .body("fetchWindowOverride", equalTo(8))
                .body("effectiveFetchWindow", equalTo(8));
    }

    @Test
    void adminSecretManagementEndpointsStayReachableWithRealPostgresPersistence() {
        BrowserSession session = loginBrowserAsAdmin();

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/admin/secret-management")
                .then()
                .statusCode(200)
                .body("mode", notNullValue())
                .body("providerId", notNullValue())
                .body("providerHealthy", equalTo(true))
                .body("providerWritable", equalTo(true))
                .body("rotationPlan.planId", notNullValue())
                .body("reencryptionRequirements", notNullValue());

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/admin/secret-management/report")
                .then()
                .statusCode(200)
                .body("status.mode", notNullValue())
                .body("status.providerWritable", equalTo(true))
                .body("exportedAt", notNullValue())
                .body("saveChecklist.size()", notNullValue());

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .cookie(CSRF_COOKIE, session.csrfToken())
                .header(CSRF_HEADER, session.csrfToken())
                .contentType("application/json")
                .body(Map.of("password", "nimda"))
                .when().post("/api/admin/secret-management/re-auth/password")
                .then()
                .statusCode(200)
                .body("reauthenticationRequired", equalTo(true))
                .body("reauthenticationSatisfied", equalTo(true))
                .body("reauthenticationExpiresAt", notNullValue());
    }

    @Test
    void adminDeleteUserEndpointRemovesOwnedDataAcrossPostgresTables() {
        DeletedUserFixture fixture = QuarkusTransaction.requiringNew().call(this::seedOwnedUserData);
        BrowserSession session = loginBrowserAsAdmin();

        assertOwnedUserDataPresent(fixture);

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .cookie(CSRF_COOKIE, session.csrfToken())
                .header(CSRF_HEADER, session.csrfToken())
                .when().delete("/api/admin/users/{userId}", fixture.userId())
                .then()
                .statusCode(200)
                .body("deleted", equalTo(true));

        QuarkusTransaction.requiringNew().run(() -> assertOwnedUserDataDeleted(fixture));
    }

    @Test
    void switchingApplicationModeRevokesOtherBrowserSessionsAndRestoresEligibleUsers() {
        ManagedUserFixture managedUser = QuarkusTransaction.requiringNew().call(this::seedManagedUserForModeSwitch);
        BrowserSession managedUserSession = loginBrowser(managedUser.username(), managedUser.password());
        BrowserSession adminSession = loginBrowserAsAdmin();

        given()
                .cookie(SESSION_COOKIE, adminSession.sessionToken())
                .cookie(CSRF_COOKIE, adminSession.csrfToken())
                .header(CSRF_HEADER, adminSession.csrfToken())
                .contentType("application/json")
                .body(Map.of("multiUserEnabled", false))
                .when().put("/api/admin/users/mode")
                .then()
                .statusCode(200)
                .body("effectiveMultiUserEnabled", equalTo(false))
                .body("multiUserEnabledOverride", equalTo(false));

        given()
                .cookie(SESSION_COOKIE, managedUserSession.sessionToken())
                .when().get("/api/app/ui-preferences")
                .then()
                .statusCode(401);

        QuarkusTransaction.requiringNew().run(() -> {
            AppUser reloadedManagedUser = appUserService.findById(managedUser.userId()).orElseThrow();
            AppUser admin = adminUser();

            assertFalse(reloadedManagedUser.active);
            assertTrue(reloadedManagedUser.disabledBySingleUserMode);
            assertTrue(admin.active);
            assertFalse(admin.disabledBySingleUserMode);
            assertEquals(0, userSessionRepository.listActiveByUserId(managedUser.userId(), Instant.now()).size());
            assertNotNull(userSessionRepository.listRecentByUserId(managedUser.userId(), 10).getFirst().revokedAt);
        });

        given()
                .cookie(SESSION_COOKIE, adminSession.sessionToken())
                .cookie(CSRF_COOKIE, adminSession.csrfToken())
                .header(CSRF_HEADER, adminSession.csrfToken())
                .contentType("application/json")
                .body(Map.of("multiUserEnabled", true))
                .when().put("/api/admin/users/mode")
                .then()
                .statusCode(200)
                .body("effectiveMultiUserEnabled", equalTo(true))
                .body("multiUserEnabledOverride", equalTo(true));

        QuarkusTransaction.requiringNew().run(() -> {
            AppUser reloadedManagedUser = appUserService.findById(managedUser.userId()).orElseThrow();

            assertTrue(reloadedManagedUser.active);
            assertFalse(reloadedManagedUser.disabledBySingleUserMode);
            assertEquals(0, userSessionRepository.listActiveByUserId(managedUser.userId(), Instant.now()).size());
        });
    }

    @Test
    void revokeOtherSessionsRevokesBrowserAndRemoteSessionsButKeepsCurrentBrowserSession() {
        BrowserSession currentBrowserSession = loginBrowserAsAdmin();
        BrowserSession otherBrowserSession = loginBrowserAsAdmin();
        RemoteBrowserSession remoteSession = loginRemoteAsAdmin();

        given()
                .cookie(SESSION_COOKIE, currentBrowserSession.sessionToken())
                .cookie(CSRF_COOKIE, currentBrowserSession.csrfToken())
                .header(CSRF_HEADER, currentBrowserSession.csrfToken())
                .when().post("/api/account/sessions/revoke-others")
                .then()
                .statusCode(204);

        given()
                .cookie(SESSION_COOKIE, currentBrowserSession.sessionToken())
                .when().get("/api/app/ui-preferences")
                .then()
                .statusCode(200);

        given()
                .cookie(SESSION_COOKIE, otherBrowserSession.sessionToken())
                .when().get("/api/app/ui-preferences")
                .then()
                .statusCode(401);

        given()
                .cookie(REMOTE_SESSION_COOKIE, remoteSession.sessionToken())
                .when().get("/api/remote/auth/me")
                .then()
                .statusCode(401);

        QuarkusTransaction.requiringNew().run(() -> {
            AppUser admin = adminUser();
            List<UserSession> browserSessions = userSessionRepository.listRecentByUserId(admin.id, 10);
            List<RemoteSession> remoteSessions = remoteSessionRepository.listRecentByUserId(admin.id, 10);

            assertTrue(browserSessions.size() >= 2);
            assertFalse(remoteSessions.isEmpty());
            assertTrue(userSessionRepository.listActiveByUserId(admin.id, Instant.now()).stream()
                    .anyMatch((session) -> session.tokenHash.equals(currentBrowserSession.sessionTokenHash())));
            assertNotNull(browserSessions.stream()
                    .filter((session) -> session.tokenHash.equals(otherBrowserSession.sessionTokenHash()))
                    .findFirst()
                    .orElseThrow()
                    .revokedAt);
            assertNotNull(remoteSessions.stream()
                    .filter((session) -> session.tokenHash.equals(remoteSession.sessionTokenHash()))
                    .findFirst()
                    .orElseThrow()
                    .revokedAt);
        });
    }

    @Test
    void accountSessionsEndpointListsAndRevokesSpecificBrowserAndRemoteSessions() {
        BrowserSession currentBrowserSession = loginBrowserAsAdmin();
        BrowserSession otherBrowserSession = loginBrowserAsAdmin();
        RemoteBrowserSession remoteSession = loginRemoteAsAdmin();

        UserSession persistedOtherBrowserSession = QuarkusTransaction.requiringNew()
                .call(() -> findBrowserSessionByHash(otherBrowserSession.sessionTokenHash()));
        RemoteSession persistedRemoteSession = QuarkusTransaction.requiringNew()
                .call(() -> findRemoteSessionByHash(remoteSession.sessionTokenHash()));

        given()
                .cookie(SESSION_COOKIE, currentBrowserSession.sessionToken())
                .when().get("/api/account/sessions")
                .then()
                .statusCode(200)
                .body("activeSessions.find { it.id == " + persistedOtherBrowserSession.id + " && it.sessionType == 'BROWSER' }.current", equalTo(false))
                .body("activeSessions.find { it.id == " + persistedRemoteSession.id + " && it.sessionType == 'REMOTE' }.current", equalTo(false))
                .body("activeSessions.find { it.current == true }.sessionType", equalTo("BROWSER"))
                .body("recentLogins.find { it.id == " + persistedOtherBrowserSession.id + " && it.sessionType == 'BROWSER' }.active", equalTo(true))
                .body("recentLogins.find { it.id == " + persistedRemoteSession.id + " && it.sessionType == 'REMOTE' }.active", equalTo(true));

        given()
                .cookie(SESSION_COOKIE, currentBrowserSession.sessionToken())
                .cookie(CSRF_COOKIE, currentBrowserSession.csrfToken())
                .header(CSRF_HEADER, currentBrowserSession.csrfToken())
                .queryParam("type", "BROWSER")
                .when().post("/api/account/sessions/{sessionId}/revoke", persistedOtherBrowserSession.id)
                .then()
                .statusCode(204);

        given()
                .cookie(SESSION_COOKIE, currentBrowserSession.sessionToken())
                .cookie(CSRF_COOKIE, currentBrowserSession.csrfToken())
                .header(CSRF_HEADER, currentBrowserSession.csrfToken())
                .queryParam("type", "REMOTE")
                .when().post("/api/account/sessions/{sessionId}/revoke", persistedRemoteSession.id)
                .then()
                .statusCode(204);

        given()
                .cookie(SESSION_COOKIE, currentBrowserSession.sessionToken())
                .when().get("/api/app/ui-preferences")
                .then()
                .statusCode(200);

        given()
                .cookie(SESSION_COOKIE, otherBrowserSession.sessionToken())
                .when().get("/api/app/ui-preferences")
                .then()
                .statusCode(401);

        given()
                .cookie(REMOTE_SESSION_COOKIE, remoteSession.sessionToken())
                .when().get("/api/remote/auth/me")
                .then()
                .statusCode(401);

        QuarkusTransaction.requiringNew().run(() -> {
            UserSession reloadedBrowserSession = findBrowserSessionByHash(otherBrowserSession.sessionTokenHash());
            RemoteSession reloadedRemoteSession = findRemoteSessionByHash(remoteSession.sessionTokenHash());

            assertNotNull(reloadedBrowserSession.revokedAt);
            assertNotNull(reloadedRemoteSession.revokedAt);
            assertTrue(userSessionRepository.listActiveByUserId(adminUser().id, Instant.now()).stream()
                    .anyMatch((session) -> session.tokenHash.equals(currentBrowserSession.sessionTokenHash())));
        });
    }

    private BrowserSession loginBrowserAsAdmin() {
        return loginBrowser("admin", "nimda");
    }

    private BrowserSession loginBrowser(String username, String password) {
        ValidatableResponse response = given()
                .contentType("application/json")
                .body(Map.of(
                        "username", username,
                        "password", password))
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTHENTICATED"));

        String sessionToken = response.extract().cookie(SESSION_COOKIE);
        String csrfToken = response.extract().cookie(CSRF_COOKIE);
        assertNotNull(sessionToken);
        assertNotNull(csrfToken);
        return new BrowserSession(sessionToken, csrfToken, sha256(sessionToken));
    }

    private RemoteBrowserSession loginRemoteAsAdmin() {
        ValidatableResponse response = given()
                .contentType("application/json")
                .body(Map.of(
                        "username", "admin",
                        "password", "nimda"))
                .when().post("/api/remote/auth/login")
                .then()
                .statusCode(200)
                .body("username", equalTo("admin"))
                .body("role", equalTo("ADMIN"));

        String sessionToken = response.extract().cookie(REMOTE_SESSION_COOKIE);
        String csrfToken = response.extract().cookie(REMOTE_CSRF_COOKIE);
        assertNotNull(sessionToken);
        assertNotNull(csrfToken);
        return new RemoteBrowserSession(sessionToken, csrfToken, sha256(sessionToken));
    }

    private ExtensionAuthSession loginExtensionAsAdmin(String label, String browserFamily, String extensionVersion) {
        ValidatableResponse response = given()
                .contentType("application/json")
                .body(Map.of(
                        "username", "admin",
                        "password", "nimda",
                        "label", label,
                        "browserFamily", browserFamily,
                        "extensionVersion", extensionVersion))
                .when().post("/api/extension/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTHENTICATED"))
                .body("session.user.username", equalTo("admin"))
                .body("session.tokens.accessToken", notNullValue())
                .body("session.tokens.refreshToken", notNullValue());

        return new ExtensionAuthSession(
                response.extract().path("session.tokens.accessToken"),
                response.extract().path("session.tokens.refreshToken"));
    }

    private AppUser adminUser() {
        return appUserService.findByUsername("admin").orElseThrow();
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.Base64.getEncoder().encodeToString(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not hash session token for assertions", e);
        }
    }

    private String pkceS256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Could not derive PKCE challenge for assertions", e);
        }
    }

    private UserSession findBrowserSessionByHash(String tokenHash) {
        return userSessionRepository.listRecentByUserId(adminUser().id, 20).stream()
                .filter((session) -> session.tokenHash.equals(tokenHash))
                .findFirst()
                .orElseThrow();
    }

    private RemoteSession findRemoteSessionByHash(String tokenHash) {
        return remoteSessionRepository.listRecentByUserId(adminUser().id, 20).stream()
                .filter((session) -> session.tokenHash.equals(tokenHash))
                .findFirst()
                .orElseThrow();
    }

    private DeletedUserFixture seedOwnedUserData() {
        AppUser doomedUser = appUserService.createUser(new dev.inboxbridge.dto.CreateUserRequest(
                "delete-me@example.com",
                "DeleteMe#123",
                "USER"));
        Instant now = Instant.now();
        String sourceId = "delete-source-" + doomedUser.id;
        String destinationKey = "destination:delete-" + doomedUser.id;
        String destinationIdentityKey = destinationKey + ":identity";

        UserEmailAccount sourceAccount = new UserEmailAccount();
        sourceAccount.userId = doomedUser.id;
        sourceAccount.emailAccountId = sourceId;
        sourceAccount.enabled = true;
        sourceAccount.enableAfterOauthConnect = false;
        sourceAccount.protocol = InboxBridgeConfig.Protocol.IMAP;
        sourceAccount.host = "imap.delete-me.test";
        sourceAccount.port = 993;
        sourceAccount.tls = true;
        sourceAccount.authMethod = InboxBridgeConfig.AuthMethod.PASSWORD;
        sourceAccount.oauthProvider = InboxBridgeConfig.OAuthProvider.NONE;
        sourceAccount.username = "delete-me@example.com";
        sourceAccount.passwordCiphertext = "ciphertext-source";
        sourceAccount.passwordNonce = "nonce-source";
        sourceAccount.folderName = "INBOX";
        sourceAccount.unreadOnly = true;
        sourceAccount.fetchMode = SourceFetchMode.POLLING;
        sourceAccount.customLabel = "Delete me source";
        sourceAccount.markReadAfterPoll = false;
        sourceAccount.postPollAction = SourcePostPollAction.NONE;
        sourceAccount.createdAt = now;
        sourceAccount.updatedAt = now;
        userEmailAccountRepository.persist(sourceAccount);

        UserMailDestinationConfig destinationConfig = new UserMailDestinationConfig();
        destinationConfig.userId = doomedUser.id;
        destinationConfig.provider = "GENERIC_IMAP";
        destinationConfig.host = "imap.destination.test";
        destinationConfig.port = 993;
        destinationConfig.tls = true;
        destinationConfig.authMethod = "PASSWORD";
        destinationConfig.oauthProvider = "NONE";
        destinationConfig.username = "destination@example.com";
        destinationConfig.passwordCiphertext = "ciphertext-destination";
        destinationConfig.passwordNonce = "nonce-destination";
        destinationConfig.folderName = "INBOX";
        destinationConfig.keyVersion = "LOCAL";
        destinationConfig.updatedAt = now;
        userMailDestinationConfigRepository.persist(destinationConfig);

        UserGmailConfig gmailConfig = new UserGmailConfig();
        gmailConfig.userId = doomedUser.id;
        gmailConfig.destinationUser = "delete-me@gmail.test";
        gmailConfig.linkedMailboxAddress = "linked@example.com";
        gmailConfig.clientIdCiphertext = "ciphertext-client-id";
        gmailConfig.clientIdNonce = "nonce-client-id";
        gmailConfig.clientSecretCiphertext = "ciphertext-client-secret";
        gmailConfig.clientSecretNonce = "nonce-client-secret";
        gmailConfig.refreshTokenCiphertext = "ciphertext-refresh";
        gmailConfig.refreshTokenNonce = "nonce-refresh";
        gmailConfig.keyVersion = "LOCAL";
        gmailConfig.redirectUri = "https://localhost/oauth/callback";
        gmailConfig.createMissingLabels = true;
        gmailConfig.neverMarkSpam = true;
        gmailConfig.processForCalendar = false;
        gmailConfig.updatedAt = now;
        userGmailConfigRepository.persist(gmailConfig);

        UserPollingSetting userPollingSetting = new UserPollingSetting();
        userPollingSetting.userId = doomedUser.id;
        userPollingSetting.pollEnabledOverride = Boolean.TRUE;
        userPollingSetting.pollIntervalOverride = "PT15M";
        userPollingSetting.fetchWindowOverride = 25;
        userPollingSetting.updatedAt = now;
        userPollingSettingRepository.persist(userPollingSetting);

        UserUiPreference uiPreference = new UserUiPreference();
        uiPreference.userId = doomedUser.id;
        uiPreference.persistLayout = true;
        uiPreference.layoutEditEnabled = true;
        uiPreference.quickSetupCollapsed = false;
        uiPreference.quickSetupDismissed = false;
        uiPreference.quickSetupPinnedVisible = true;
        uiPreference.adminQuickSetupDismissed = false;
        uiPreference.adminQuickSetupPinnedVisible = true;
        uiPreference.destinationMailboxCollapsed = false;
        uiPreference.userPollingCollapsed = false;
        uiPreference.userStatsCollapsed = false;
        uiPreference.sourceEmailAccountsCollapsed = false;
        uiPreference.adminQuickSetupCollapsed = false;
        uiPreference.systemDashboardCollapsed = false;
        uiPreference.oauthAppsCollapsed = false;
        uiPreference.globalStatsCollapsed = false;
        uiPreference.userManagementCollapsed = false;
        uiPreference.userSectionOrder = "destination,sourceEmailAccounts";
        uiPreference.adminSectionOrder = "globalStats,userManagement";
        uiPreference.language = "en";
        uiPreference.themeMode = "SYSTEM";
        uiPreference.dateFormat = "YYYY-MM-DD HH:mm";
        uiPreference.timezoneMode = "AUTO";
        uiPreference.timezone = "UTC";
        uiPreference.notificationHistory = "[]";
        uiPreference.updatedAt = now;
        userUiPreferenceRepository.persist(uiPreference);

        UserPasskey passkey = new UserPasskey();
        passkey.userId = doomedUser.id;
        passkey.label = "Delete me key";
        passkey.credentialId = "credential-" + doomedUser.id;
        passkey.publicKeyCose = "pk-cose";
        passkey.signatureCount = 1;
        passkey.discoverable = true;
        passkey.backupEligible = false;
        passkey.backedUp = false;
        passkey.createdAt = now;
        userPasskeyRepository.persist(passkey);

        UserSession browserSession = new UserSession();
        browserSession.userId = doomedUser.id;
        browserSession.tokenHash = "user-session-token-" + doomedUser.id;
        browserSession.csrfTokenHash = "user-session-csrf-" + doomedUser.id;
        browserSession.createdAt = now;
        browserSession.expiresAt = now.plusSeconds(3600);
        browserSession.lastSeenAt = now;
        browserSession.clientIp = "127.0.0.1";
        browserSession.loginMethod = UserSession.LoginMethod.PASSWORD;
        userSessionRepository.persist(browserSession);

        extensionSessionRepository.persist(extensionSession(doomedUser.id, now));
        remoteSessionRepository.persist(remoteSession(doomedUser.id, now));

        SourcePollingSetting sourcePollingSetting = new SourcePollingSetting();
        sourcePollingSetting.sourceId = sourceId;
        sourcePollingSetting.ownerUserId = doomedUser.id;
        sourcePollingSetting.pollEnabledOverride = Boolean.TRUE;
        sourcePollingSetting.pollIntervalOverride = "PT5M";
        sourcePollingSetting.fetchWindowOverride = 10;
        sourcePollingSetting.updatedAt = now;
        sourcePollingSettingRepository.persist(sourcePollingSetting);

        SourcePollingState sourcePollingState = new SourcePollingState();
        sourcePollingState.sourceId = sourceId;
        sourcePollingState.nextPollAt = now.plusSeconds(300);
        sourcePollingState.consecutiveFailures = 1;
        sourcePollingState.lastFailureReason = "temporary";
        sourcePollingState.lastFailureAt = now;
        sourcePollingState.lastSuccessAt = now.minusSeconds(60);
        sourcePollingState.imapFolderName = "INBOX";
        sourcePollingState.imapCheckpointDestinationKey = destinationKey;
        sourcePollingState.imapUidValidity = 1L;
        sourcePollingState.imapLastSeenUid = 42L;
        sourcePollingState.updatedAt = now;
        sourcePollingStateRepository.persist(sourcePollingState);

        SourceImapCheckpoint sourceImapCheckpoint = new SourceImapCheckpoint();
        sourceImapCheckpoint.sourceId = sourceId;
        sourceImapCheckpoint.destinationKey = destinationKey;
        sourceImapCheckpoint.folderName = "INBOX";
        sourceImapCheckpoint.uidValidity = 1L;
        sourceImapCheckpoint.lastSeenUid = 42L;
        sourceImapCheckpoint.updatedAt = now;
        sourceImapCheckpointRepository.persist(sourceImapCheckpoint);

        SourcePollEvent sourcePollEvent = new SourcePollEvent();
        sourcePollEvent.sourceId = sourceId;
        sourcePollEvent.triggerName = "manual";
        sourcePollEvent.status = "SUCCESS";
        sourcePollEvent.startedAt = now.minusSeconds(30);
        sourcePollEvent.finishedAt = now;
        sourcePollEvent.fetchedCount = 1;
        sourcePollEvent.importedCount = 1;
        sourcePollEvent.importedBytes = 512L;
        sourcePollEvent.duplicateCount = 0;
        sourcePollEvent.spamJunkMessageCount = 0;
        sourcePollEvent.actorUsername = "admin";
        sourcePollEvent.executionSurface = "admin-ui";
        sourcePollEvent.errorMessage = null;
        sourcePollEventRepository.persist(sourcePollEvent);

        ImportedMessage importedMessage = new ImportedMessage();
        importedMessage.sourceAccountId = sourceId;
        importedMessage.sourceMessageKey = "message-1";
        importedMessage.messageIdHeader = "<message-1@example.test>";
        importedMessage.rawSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        importedMessage.destinationKey = destinationKey;
        importedMessage.destinationIdentityKey = destinationIdentityKey;
        importedMessage.gmailMessageId = "gmail-message-1";
        importedMessage.gmailThreadId = "gmail-thread-1";
        importedMessage.importedAt = now;
        importedMessageRepository.persist(importedMessage);

        persistCredential("GOOGLE", "source-google:" + sourceId, now);
        persistCredential("MICROSOFT", sourceId, now);
        persistCredential("GOOGLE", "user-gmail:" + doomedUser.id, now);
        persistCredential("MICROSOFT", "destination-microsoft:" + doomedUser.id, now);

        return new DeletedUserFixture(doomedUser.id, sourceId);
    }

    private ManagedUserFixture seedManagedUserForModeSwitch() {
        AppUser managedUser = appUserService.createUser(new dev.inboxbridge.dto.CreateUserRequest(
                "mode-switch-user@example.com",
                "ModeSwitch#123",
                "USER"));
        managedUser.mustChangePassword = false;
        managedUser.updatedAt = Instant.now();
        return new ManagedUserFixture(managedUser.id, managedUser.username, "ModeSwitch#123");
    }

    private ConfiguredMailboxUserFixture seedConfiguredMailboxUser() {
        String fixtureSuffix = Long.toUnsignedString(System.nanoTime());
        AppUser configuredUser = appUserService.createUser(new dev.inboxbridge.dto.CreateUserRequest(
                "configured-mailbox-user-" + fixtureSuffix + "@example.com",
                "Configured#123",
                "USER"));
        Instant now = Instant.now();
        String sourceId = "configured-source-" + configuredUser.id;
        SecretEncryptionService.EncryptedValue encryptedDestinationPassword = secretEncryptionService.encrypt(
                "DestinationConfig#123",
                "user-destination:" + configuredUser.id + ":password");
        SecretEncryptionService.EncryptedValue encryptedSourcePassword = secretEncryptionService.encrypt(
                "SourceConfig#123",
                "user-bridge:" + configuredUser.id + ":" + sourceId + ":password");
        String activeKeyVersion = secretEncryptionService.keyVersion();

        UserMailDestinationConfig destinationConfig = new UserMailDestinationConfig();
        destinationConfig.userId = configuredUser.id;
        destinationConfig.provider = "GENERIC_IMAP";
        destinationConfig.host = "imap.destination-config.test";
        destinationConfig.port = 993;
        destinationConfig.tls = true;
        destinationConfig.authMethod = "PASSWORD";
        destinationConfig.oauthProvider = "NONE";
        destinationConfig.username = "destination-config@example.com";
        destinationConfig.passwordCiphertext = encryptedDestinationPassword.ciphertextBase64();
        destinationConfig.passwordNonce = encryptedDestinationPassword.nonceBase64();
        destinationConfig.folderName = "Archive/InboxBridge";
        destinationConfig.keyVersion = activeKeyVersion;
        destinationConfig.updatedAt = now;
        userMailDestinationConfigRepository.persist(destinationConfig);

        UserEmailAccount sourceAccount = new UserEmailAccount();
        sourceAccount.userId = configuredUser.id;
        sourceAccount.emailAccountId = sourceId;
        sourceAccount.enabled = true;
        sourceAccount.enableAfterOauthConnect = false;
        sourceAccount.protocol = InboxBridgeConfig.Protocol.IMAP;
        sourceAccount.host = "imap.source-config.test";
        sourceAccount.port = 993;
        sourceAccount.tls = true;
        sourceAccount.authMethod = InboxBridgeConfig.AuthMethod.PASSWORD;
        sourceAccount.oauthProvider = InboxBridgeConfig.OAuthProvider.NONE;
        sourceAccount.username = "source-config@example.com";
        sourceAccount.passwordCiphertext = encryptedSourcePassword.ciphertextBase64();
        sourceAccount.passwordNonce = encryptedSourcePassword.nonceBase64();
        sourceAccount.keyVersion = activeKeyVersion;
        sourceAccount.folderName = "Projects/InboxBridge";
        sourceAccount.unreadOnly = true;
        sourceAccount.fetchMode = SourceFetchMode.POLLING;
        sourceAccount.customLabel = "Config endpoint source";
        sourceAccount.markReadAfterPoll = false;
        sourceAccount.postPollAction = SourcePostPollAction.MOVE;
        sourceAccount.postPollTargetFolder = "Processed";
        sourceAccount.createdAt = now;
        sourceAccount.updatedAt = now;
        userEmailAccountRepository.persist(sourceAccount);

        UserPollingSetting userPollingSetting = new UserPollingSetting();
        userPollingSetting.userId = configuredUser.id;
        userPollingSetting.pollEnabledOverride = Boolean.FALSE;
        userPollingSetting.pollIntervalOverride = "PT20M";
        userPollingSetting.fetchWindowOverride = 30;
        userPollingSetting.updatedAt = now;
        userPollingSettingRepository.persist(userPollingSetting);

        SourcePollingSetting sourcePollingSetting = new SourcePollingSetting();
        sourcePollingSetting.sourceId = sourceId;
        sourcePollingSetting.ownerUserId = configuredUser.id;
        sourcePollingSetting.pollEnabledOverride = Boolean.TRUE;
        sourcePollingSetting.pollIntervalOverride = "PT5M";
        sourcePollingSetting.fetchWindowOverride = 8;
        sourcePollingSetting.updatedAt = now;
        sourcePollingSettingRepository.persist(sourcePollingSetting);

        return new ConfiguredMailboxUserFixture(configuredUser.username, "Configured#123", sourceId);
    }

    private void persistCredential(String provider, String subjectKey, Instant now) {
        OAuthCredential credential = new OAuthCredential();
        credential.provider = provider;
        credential.subjectKey = subjectKey;
        credential.keyVersion = "LOCAL";
        credential.refreshTokenCiphertext = "ciphertext";
        credential.refreshTokenNonce = "nonce";
        credential.accessTokenCiphertext = "ciphertext-access";
        credential.accessTokenNonce = "nonce-access";
        credential.accessExpiresAt = now.plusSeconds(3600);
        credential.tokenScope = "scope";
        credential.tokenType = "Bearer";
        credential.createdAt = now;
        credential.updatedAt = now;
        oAuthCredentialRepository.persist(credential);
    }

    private ExtensionSession extensionSession(Long userId, Instant now) {
        ExtensionSession session = new ExtensionSession();
        session.userId = userId;
        session.label = "Delete me extension";
        session.browserFamily = "chrome";
        session.extensionVersion = "1.0.0";
        session.tokenHash = "extension-token-" + userId;
        session.tokenPrefix = "ibx_delete";
        session.accessExpiresAt = now.plusSeconds(3600);
        session.refreshTokenHash = "extension-refresh-" + userId;
        session.createdAt = now;
        session.lastUsedAt = now;
        session.expiresAt = now.plusSeconds(7200);
        return session;
    }

    private RemoteSession remoteSession(Long userId, Instant now) {
        RemoteSession session = new RemoteSession();
        session.userId = userId;
        session.tokenHash = "remote-token-" + userId;
        session.csrfTokenHash = "remote-csrf-" + userId;
        session.createdAt = now;
        session.expiresAt = now.plusSeconds(3600);
        session.lastSeenAt = now;
        session.clientIp = "127.0.0.1";
        session.locationLabel = "Lisbon";
        session.userAgent = "JUnit";
        session.loginMethod = UserSession.LoginMethod.PASSWORD;
        return session;
    }

    private void assertOwnedUserDataPresent(DeletedUserFixture fixture) {
        QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(appUserService.findById(fixture.userId()).isPresent());
            assertEquals(1, userEmailAccountRepository.listByUserId(fixture.userId()).size());
            assertTrue(userMailDestinationConfigRepository.findByUserId(fixture.userId()).isPresent());
            assertTrue(userGmailConfigRepository.findByUserId(fixture.userId()).isPresent());
            assertTrue(userPollingSettingRepository.findByUserId(fixture.userId()).isPresent());
            assertTrue(userUiPreferenceRepository.findByUserId(fixture.userId()).isPresent());
            assertEquals(1, userPasskeyRepository.countByUserId(fixture.userId()));
            assertEquals(1, userSessionRepository.listRecentByUserId(fixture.userId(), 10).size());
            assertEquals(1, extensionSessionRepository.listByUserId(fixture.userId()).size());
            assertEquals(1, remoteSessionRepository.listRecentByUserId(fixture.userId(), 10).size());
            assertTrue(sourcePollingSettingRepository.findBySourceId(fixture.sourceId()).isPresent());
            assertTrue(sourcePollingStateRepository.findBySourceId(fixture.sourceId()).isPresent());
            assertTrue(sourceImapCheckpointRepository.findByScope(fixture.sourceId(), fixture.destinationKey(), "INBOX").isPresent());
            assertTrue(sourcePollEventRepository.findLatestBySourceId(fixture.sourceId()).isPresent());
            assertEquals(1, importedMessageRepository.countByDestinationKeyAndSourceAccountId(fixture.destinationKey(), fixture.sourceId()));
            assertTrue(oAuthCredentialRepository.findByProviderAndSubject("GOOGLE", "source-google:" + fixture.sourceId()).isPresent());
            assertTrue(oAuthCredentialRepository.findByProviderAndSubject("MICROSOFT", fixture.sourceId()).isPresent());
            assertTrue(oAuthCredentialRepository.findByProviderAndSubject("GOOGLE", "user-gmail:" + fixture.userId()).isPresent());
            assertTrue(oAuthCredentialRepository.findByProviderAndSubject("MICROSOFT", "destination-microsoft:" + fixture.userId()).isPresent());
        });
    }

    private void assertOwnedUserDataDeleted(DeletedUserFixture fixture) {
        assertTrue(appUserService.findById(fixture.userId()).isEmpty());
        assertEquals(0, userEmailAccountRepository.listByUserId(fixture.userId()).size());
        assertTrue(userMailDestinationConfigRepository.findByUserId(fixture.userId()).isEmpty());
        assertTrue(userGmailConfigRepository.findByUserId(fixture.userId()).isEmpty());
        assertTrue(userPollingSettingRepository.findByUserId(fixture.userId()).isEmpty());
        assertTrue(userUiPreferenceRepository.findByUserId(fixture.userId()).isEmpty());
        assertEquals(0, userPasskeyRepository.countByUserId(fixture.userId()));
        assertEquals(0, userSessionRepository.listRecentByUserId(fixture.userId(), 10).size());
        assertEquals(0, extensionSessionRepository.listByUserId(fixture.userId()).size());
        assertEquals(0, remoteSessionRepository.listRecentByUserId(fixture.userId(), 10).size());
        assertTrue(sourcePollingSettingRepository.findBySourceId(fixture.sourceId()).isEmpty());
        assertTrue(sourcePollingStateRepository.findBySourceId(fixture.sourceId()).isEmpty());
        assertTrue(sourceImapCheckpointRepository.findByScope(fixture.sourceId(), fixture.destinationKey(), "INBOX").isEmpty());
        assertTrue(sourcePollEventRepository.findLatestBySourceId(fixture.sourceId()).isEmpty());
        assertEquals(0, importedMessageRepository.countByDestinationKeyAndSourceAccountId(fixture.destinationKey(), fixture.sourceId()));
        assertTrue(oAuthCredentialRepository.findByProviderAndSubject("GOOGLE", "source-google:" + fixture.sourceId()).isEmpty());
        assertTrue(oAuthCredentialRepository.findByProviderAndSubject("MICROSOFT", fixture.sourceId()).isEmpty());
        assertTrue(oAuthCredentialRepository.findByProviderAndSubject("GOOGLE", "user-gmail:" + fixture.userId()).isEmpty());
        assertTrue(oAuthCredentialRepository.findByProviderAndSubject("MICROSOFT", "destination-microsoft:" + fixture.userId()).isEmpty());
    }

    private record BrowserSession(String sessionToken, String csrfToken, String sessionTokenHash) {
    }

    private record RemoteBrowserSession(String sessionToken, String csrfToken, String sessionTokenHash) {
    }

    private record ExtensionAuthSession(String accessToken, String refreshToken) {
    }

    private record DeletedUserFixture(Long userId, String sourceId) {
        private String destinationKey() {
            return "destination:delete-" + userId;
        }
    }

    private record ManagedUserFixture(Long userId, String username, String password) {
    }

    private record ConfiguredMailboxUserFixture(String username, String password, String sourceId) {
    }
}
