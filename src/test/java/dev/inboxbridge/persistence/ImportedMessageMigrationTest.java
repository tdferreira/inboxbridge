package dev.inboxbridge.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ImportedMessageMigrationTest {

    @Test
    void followUpMigrationDropsLegacyDestinationScopedUniqueIndexes() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V53__drop_legacy_destination_dedupe_indexes.sql"));

        assertTrue(migration.contains("drop index if exists uk_imported_message_destination_sha;"));
        assertTrue(migration.contains("drop index if exists uk_imported_message_destination_source_key;"));
    }

    @Test
    void messageIdDedupeMigrationAddsDestinationIdentityMessageIdIndex() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V54__imported_message_message_id_dedupe_index.sql"));

        assertTrue(migration.contains("create index if not exists idx_imported_message_destination_identity_message_id"));
        assertTrue(migration.contains("on imported_message(destination_identity_key, source_account_id, message_id_header);"));
    }
}
