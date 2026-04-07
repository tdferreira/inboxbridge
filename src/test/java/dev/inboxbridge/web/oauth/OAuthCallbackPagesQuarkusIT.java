package dev.inboxbridge.web.oauth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class OAuthCallbackPagesQuarkusIT {

    @Test
    void googleCallbackErrorPageRendersAtRuntime() {
        given()
                .queryParam("error", "access_denied")
                .queryParam("error_description", "user denied")
                .when().get("/api/google-oauth/callback")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Google OAuth Permission Required"))
                .body(containsString("Return to InboxBridge"));
    }

    @Test
    void microsoftCallbackErrorPageRendersAtRuntime() {
        given()
                .queryParam("error", "access_denied")
                .queryParam("error_description", "user denied")
                .when().get("/api/microsoft-oauth/callback")
                .then()
                .statusCode(200)
                .contentType(containsString("text/html"))
                .body(containsString("Microsoft OAuth Permission Required"))
                .body(containsString("Return to InboxBridge"));
    }
}
