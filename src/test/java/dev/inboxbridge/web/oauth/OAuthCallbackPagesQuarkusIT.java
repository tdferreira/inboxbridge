package dev.inboxbridge.web.oauth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.endsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class OAuthCallbackPagesQuarkusIT {

    @Test
    void googleCallbackRedirectsToFrontendAtRuntime() {
        given()
                .redirects().follow(false)
                .queryParam("error", "access_denied")
                .queryParam("error_description", "user denied")
                .when().get("/api/google-oauth/callback")
                .then()
                .statusCode(303)
                .header("Location", endsWith("/oauth/google/callback?lang=en&error=access_denied&error_description=user+denied"));
    }

    @Test
    void microsoftCallbackRedirectsToFrontendAtRuntime() {
        given()
                .redirects().follow(false)
                .queryParam("error", "access_denied")
                .queryParam("error_description", "user denied")
                .when().get("/api/microsoft-oauth/callback")
                .then()
                .statusCode(303)
                .header("Location", endsWith("/oauth/microsoft/callback?lang=en&error=access_denied&error_description=user+denied"));
    }
}
