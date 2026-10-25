package dev.inboxbridge.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class HealthResourceQuarkusIT {

    @Test
    void smallRyeHealthReportsUp() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }

    @Test
    void publicHealthSummaryReportsLivenessWithoutMailboxStatistics() {
        given()
                .when().get("/api/health/summary")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("$", not(hasKey("importedMessages")));
    }
}
