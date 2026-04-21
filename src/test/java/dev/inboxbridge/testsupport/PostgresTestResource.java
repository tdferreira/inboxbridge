package dev.inboxbridge.testsupport;

import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Boots a disposable PostgreSQL instance so selected Quarkus tests can verify
 * Flyway and persistence behavior against the same database engine used in
 * production instead of relying on the lightweight H2 test profile.
 */
public final class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse(
                System.getProperty("inboxbridge.test.postgres.image", "postgres:16")))
                .withDatabaseName("inboxbridge_test")
                .withUsername("inboxbridge")
                .withPassword("inboxbridge");
        postgres.start();

        return Map.of(
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword());
    }

    @Override
    public void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }
}
