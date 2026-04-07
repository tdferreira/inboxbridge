package dev.inboxbridge.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class AuthSessionEndpointsQuarkusIT {

    @Test
    void authOptionsExposeRuntimeUiConfiguration() {
        given()
                .when().get("/api/auth/options")
                .then()
                .statusCode(200)
                .body("multiUserEnabled", is(true))
                .body("registrationChallengeEnabled", is(true))
                .body("registrationChallengeProvider", is("ALTCHA"))
                .body("sourceOAuthProviders", notNullValue());
    }

    @Test
    void browserSessionEndpointRejectsAnonymousRuntimeAccess() {
        given()
                .when().get("/api/auth/me")
                .then()
                .statusCode(401);
    }
}
