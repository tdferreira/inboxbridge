package dev.inboxbridge.web.admin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.response.ValidatableResponse;

@QuarkusIntegrationTest
class AdminEndpointsQuarkusIT {

    private static final String SESSION_COOKIE = "inboxbridge_session";
    private static final String CSRF_COOKIE = "inboxbridge_csrf";
    private static final String CSRF_HEADER = "X-InboxBridge-CSRF";

    @Test
    void dashboardRejectsAnonymousAccess() {
        given()
                .when().get("/api/admin/dashboard")
                .then()
                .statusCode(401);
    }

    @Test
    void userManagementRejectsAnonymousAccess() {
        given()
                .when().get("/api/admin/users")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementRejectsAnonymousAccess() {
        given()
                .when().get("/api/admin/secret-management")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementReportRejectsAnonymousAccess() {
        given()
                .when().get("/api/admin/secret-management/report")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementMigrationGuideRejectsAnonymousAccess() {
        given()
                .queryParam("targetMode", "VAULT_TRANSIT")
                .when().get("/api/admin/secret-management/migration-guide")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementRecoveryGuideRejectsAnonymousAccess() {
        given()
                .when().get("/api/admin/secret-management/recovery-guide")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementRecoveryReviewRejectsAnonymousAccess() {
        given()
                .when().post("/api/admin/secret-management/recovery-review")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementRetirementReviewRejectsAnonymousAccess() {
        given()
                .when().post("/api/admin/secret-management/retirement-review")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementRetirementCompletionRejectsAnonymousAccess() {
        given()
                .when().post("/api/admin/secret-management/retirement-complete")
                .then()
                .statusCode(401);
    }

    @Test
    void reencryptStoredSecretsRejectsAnonymousAccess() {
        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/api/admin/secret-management/re-encrypt")
                .then()
                .statusCode(401);
    }

    @Test
    void approveQueuedSecretReencryptionRejectsAnonymousAccess() {
        given()
                .when().post("/api/admin/secret-management/re-encrypt/approve")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementPasswordReauthRejectsAnonymousAccess() {
        given()
                .contentType("application/json")
                .body("{\"password\":\"Current1!\"}")
                .when().post("/api/admin/secret-management/re-auth/password")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementPasskeyReauthStartRejectsAnonymousAccess() {
        given()
                .when().post("/api/admin/secret-management/re-auth/passkey/options")
                .then()
                .statusCode(401);
    }

    @Test
    void secretManagementPasskeyReauthFinishRejectsAnonymousAccess() {
        given()
                .contentType("application/json")
                .body("{\"ceremonyId\":\"ceremony-1\",\"credentialJson\":\"{}\"}")
                .when().post("/api/admin/secret-management/re-auth/passkey/verify")
                .then()
                .statusCode(401);
    }

    @Test
    void authenticatedAdminCanLoadSecretManagementStatus() {
        AuthenticatedBrowserSession session = loginAsAdmin();

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/admin/secret-management")
                .then()
                .statusCode(200)
                .body("mode", notNullValue())
                .body("providerId", notNullValue())
                .body("reencryptionRequirements", notNullValue())
                .body("retirementRequirements", notNullValue());
    }

    @Test
    void authenticatedAdminCanExportSecretManagementReport() {
        AuthenticatedBrowserSession session = loginAsAdmin();

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/admin/secret-management/report")
                .then()
                .statusCode(200)
                .body("exportedAt", notNullValue())
                .body("status.mode", notNullValue())
                .body("status.providerId", notNullValue());
    }

    @Test
    void authenticatedAdminCanCompletePasswordStepUpVerificationForSecretManagement() {
        AuthenticatedBrowserSession session = loginAsAdmin();

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

    private static AuthenticatedBrowserSession loginAsAdmin() {
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
        if (sessionToken == null || csrfToken == null) {
            throw new IllegalStateException("Expected authenticated browser-session cookies from /api/auth/login.");
        }
        return new AuthenticatedBrowserSession(sessionToken, csrfToken);
    }

    private record AuthenticatedBrowserSession(String sessionToken, String csrfToken) {
    }
}
