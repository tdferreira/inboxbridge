package dev.inboxbridge.web.remote;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class RemoteEndpointsQuarkusIT {

    @Test
    void remoteAuthSessionEndpointRejectsAnonymousRuntimeAccess() {
        given()
                .when().get("/api/remote/auth/me")
                .then()
                .statusCode(401);
    }

    @Test
    void remoteControlEndpointRejectsAnonymousRuntimeAccess() {
        given()
                .when().get("/api/remote/control")
                .then()
                .statusCode(401);
    }
}
