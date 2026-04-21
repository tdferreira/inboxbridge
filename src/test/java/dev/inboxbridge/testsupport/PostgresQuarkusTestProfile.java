package dev.inboxbridge.testsupport;

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
}
