package dev.inboxbridge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;

class SourceMailboxFoldersTest {

    @Test
    void imapFoldersUseCommaSeparatedConfiguredOrderWithoutCaseDuplicates() {
        List<String> folders = SourceMailboxFolders.forSource(
                InboxBridgeConfig.Protocol.IMAP,
                Optional.of(" INBOX, Projects/2026,\n inbox ,Archive "));

        assertEquals(List.of("INBOX", "Projects/2026", "Archive"), folders);
    }

    @Test
    void imapFoldersDefaultToInboxWhenBlank() {
        assertEquals(
                List.of("INBOX"),
                SourceMailboxFolders.forSource(InboxBridgeConfig.Protocol.IMAP, Optional.of("   ")));
    }

    @Test
    void pop3AlwaysUsesInboxOnly() {
        assertEquals(
                List.of("INBOX"),
                SourceMailboxFolders.forSource(InboxBridgeConfig.Protocol.POP3, Optional.of("Projects/2026")));
    }
}
