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
    void reencryptStoredSecretsRejectsAnonymousAccess() {
        given()
                .contentType("application/json")
                .body("{}")
                .when().post("/api/admin/secret-management/re-encrypt")
                .then()
                .statusCode(401);
    }
}
