package dev.inboxbridge.web.polling;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class PollingEndpointsQuarkusIT {

    @Test
    void pollStatusRejectsAnonymousRuntimeAccess() {
        given()
                .when().get("/api/poll/status")
                .then()
                .statusCode(401);
    }

    @Test
    void pollLiveRejectsAnonymousRuntimeAccess() {
        given()
                .when().get("/api/poll/live")
                .then()
                .statusCode(401);
    }
}
