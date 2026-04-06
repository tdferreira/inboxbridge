package dev.inboxbridge.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class SourceImapCheckpointMigrationTest {

    @Test
    void perFolderCheckpointMigrationCreatesScopedTableAndBackfillsLegacyState() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V56__source_imap_checkpoint_table.sql"));

        assertTrue(migration.contains("create table if not exists source_imap_checkpoint"));
        assertTrue(migration.contains("unique (source_id, destination_key, folder_name)"));
        assertTrue(migration.contains("insert into source_imap_checkpoint"));
        assertTrue(migration.contains("from source_polling_state"));
    }
}
