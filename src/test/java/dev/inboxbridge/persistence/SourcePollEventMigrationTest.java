package dev.inboxbridge.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SourcePollEventMigrationTest {

    @Test
    void decisionAuditMigrationAddsCooldownAndThrottleColumns() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V55__source_poll_event_decision_audit_fields.sql"));

        assertTrue(migration.contains("add column if not exists failure_category varchar(40)"));
        assertTrue(migration.contains("add column if not exists cooldown_backoff_millis bigint"));
        assertTrue(migration.contains("add column if not exists cooldown_until timestamptz"));
        assertTrue(migration.contains("add column if not exists source_throttle_wait_millis bigint"));
        assertTrue(migration.contains("add column if not exists destination_throttle_wait_millis bigint"));
    }
}
