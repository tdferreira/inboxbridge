package dev.inboxbridge.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.ExtensionSessionRepository;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.persistence.RemoteSessionRepository;
import dev.inboxbridge.persistence.UserUiPreference;
import dev.inboxbridge.persistence.UserUiPreferenceRepository;
import dev.inboxbridge.service.admin.AppUserService;
import dev.inboxbridge.testsupport.PostgresQuarkusTestProfile;
import dev.inboxbridge.testsupport.PostgresTestResource;
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
    UserUiPreferenceRepository userUiPreferenceRepository;

    @Inject
    ExtensionSessionRepository extensionSessionRepository;

    @Inject
    RemoteSessionRepository remoteSessionRepository;

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
                .body("size()", equalTo(1))
                .body("[0].label", equalTo("QA laptop"))
                .body("[0].browserFamily", equalTo("firefox"))
                .body("[0].extensionVersion", equalTo("0.7.0"));

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
        assertEquals(1, sessions.size());
        assertNotNull(sessions.getFirst().revokedAt);
        assertFalse(refreshedRefreshToken.isBlank());
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

    private BrowserSession loginBrowserAsAdmin() {
        ValidatableResponse response = given()
                .contentType("application/json")
                .body(Map.of(
                        "username", "admin",
                        "password", "nimda"))
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTHENTICATED"));

        String sessionToken = response.extract().cookie(SESSION_COOKIE);
        String csrfToken = response.extract().cookie(CSRF_COOKIE);
        assertNotNull(sessionToken);
        assertNotNull(csrfToken);
        return new BrowserSession(sessionToken, csrfToken);
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
        return new RemoteBrowserSession(sessionToken, csrfToken);
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

    private record BrowserSession(String sessionToken, String csrfToken) {
    }

    private record RemoteBrowserSession(String sessionToken, String csrfToken) {
    }

    private record ExtensionAuthSession(String accessToken, String refreshToken) {
    }
}
