package dev.inboxbridge.web.admin;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class AdminEndpointsQuarkusIT {

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
}
