package dev.inboxbridge.web.user;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class UserConfigEndpointsQuarkusIT {

    @Test
    void destinationConfigRejectsAnonymousAccess() {
        given()
                .when().get("/api/app/destination-config")
                .then()
                .statusCode(401);
    }

    @Test
    void emailAccountsRejectAnonymousAccess() {
        given()
                .when().get("/api/app/email-accounts")
                .then()
                .statusCode(401);
    }
}
