package dev.inboxbridge.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

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
    void applicationHealthSummaryReportsUpAndStartsEmpty() {
        given()
                .when().get("/api/health/summary")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("importedMessages", is(0));
    }
}
