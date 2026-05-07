package dev.inboxbridge.testsupport;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Activates the dedicated PostgreSQL-backed test profile so Quarkus augments
 * against the same database kind used in production before the Testcontainers
 * resource injects the live JDBC endpoint.
 */
public final class PostgresQuarkusTestProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "pgtest";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                // Endpoint persistence tests should not race the real 5-second
                // poll scheduler against placeholder mailbox hosts.
                "quarkus.scheduler.enabled", "false");
    }
}
