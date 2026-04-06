package dev.inboxbridge.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class UserEmailAccountOauthEnableMigrationTest {

    @Test
    void oauthEnablePendingMigrationAddsDisabledFirstFlag() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V57__user_email_account_oauth_enable_pending.sql"));

        assertTrue(migration.contains("ALTER TABLE user_email_account"));
        assertTrue(migration.contains("enable_after_oauth_connect"));
        assertTrue(migration.contains("DEFAULT FALSE"));
    }
}
