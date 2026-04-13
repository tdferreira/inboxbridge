package dev.inboxbridge.web.extension;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Locks the extension HTTP surface behind the intended runtime auth boundary in
 * the packaged app, not just in unit-sliced resource tests.
 */
@QuarkusIntegrationTest
class ExtensionEndpointsQuarkusIT {

    @Test
    void extensionStatusEndpointRejectsAnonymousAccess() {
        given()
                .when().get("/api/extension/status")
                .then()
                .statusCode(401);
    }

    @Test
    void extensionEventsEndpointRejectsAnonymousAccess() {
        given()
                .when().get("/api/extension/events")
                .then()
                .statusCode(401);
    }

    @Test
    void extensionPollEndpointRejectsAnonymousAccess() {
        given()
                .when().post("/api/extension/poll")
                .then()
                .statusCode(401);
    }

    @Test
    void extensionSessionManagementRejectsAnonymousBrowserAccess() {
        given()
                .when().get("/api/extension/sessions")
                .then()
                .statusCode(401);

        given()
                .when().delete("/api/extension/sessions")
                .then()
                .statusCode(401);
    }

    @Test
    void browserHandoffCompletionRejectsAnonymousBrowserAccess() {
        given()
                .contentType("application/json")
                .body("""
                        {"requestId":"request-1"}
                        """)
                .when().post("/api/extension/auth/browser-handoff/complete")
                .then()
                .statusCode(401);
    }
}
