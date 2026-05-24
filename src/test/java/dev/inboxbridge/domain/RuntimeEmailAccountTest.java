package dev.inboxbridge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;

class RuntimeEmailAccountTest {

    @Test
    void sourceFoldersAppendConfiguredSpamJunkFolderWhenStrategyImportsIt() {
        RuntimeEmailAccount account = account(
                Optional.of("INBOX, Projects/2026"),
                SourceSpamJunkStrategy.IMPORT_AND_ROUTE,
                Optional.of("Junk"));

        assertEquals(List.of("INBOX", "Projects/2026", "Junk"), account.sourceFolders());
        assertTrue(account.routesSpamJunkFolder(Optional.of("junk")));
        assertFalse(account.routesSpamJunkFolder(Optional.of("INBOX")));
    }

    @Test
    void sourceFoldersDoNotDuplicateSpamJunkFolderAlreadyPresentInPrimaryFolderList() {
        RuntimeEmailAccount account = account(
                Optional.of("INBOX, Junk"),
                SourceSpamJunkStrategy.IMPORT_NORMAL,
                Optional.of("junk"));

        assertEquals(List.of("INBOX", "Junk"), account.sourceFolders());
        assertFalse(account.routesSpamJunkFolder(Optional.of("Junk")));
        assertTrue(account.isSpamJunkSourceFolder(Optional.of("Junk")));
    }

    private RuntimeEmailAccount account(
            Optional<String> folders,
            SourceSpamJunkStrategy spamJunkStrategy,
            Optional<String> spamJunkSourceFolder) {
        return new RuntimeEmailAccount(
                "source",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "",
                folders,
                false,
                SourceFetchMode.POLLING,
                Optional.empty(),
                Optional.empty(),
                spamJunkStrategy,
                spamJunkSourceFolder,
                SourcePostPollSettings.none(),
                null);
    }
}
