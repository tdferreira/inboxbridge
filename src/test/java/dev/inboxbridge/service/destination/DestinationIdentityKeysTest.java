package dev.inboxbridge.service.destination;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.service.user.UserMailDestinationConfigService;

class DestinationIdentityKeysTest {

    @Test
    void imapAppendIdentityChangesWhenMailboxUsernameChanges() {
        ImapAppendDestinationTarget first = imapTarget("user-destination:7", "first@example.com", "INBOX");
        ImapAppendDestinationTarget second = imapTarget("user-destination:7", "second@example.com", "INBOX");

        assertNotEquals(
                DestinationIdentityKeys.forTarget(first),
                DestinationIdentityKeys.forTarget(second));
    }

    @Test
    void imapAppendIdentityChangesWhenDestinationFolderChanges() {
        ImapAppendDestinationTarget first = imapTarget("user-destination:7", "first@example.com", "INBOX");
        ImapAppendDestinationTarget second = imapTarget("user-destination:7", "first@example.com", "Imported/Archive");

        assertNotEquals(
                DestinationIdentityKeys.forTarget(first),
                DestinationIdentityKeys.forTarget(second));
    }

    @Test
    void gmailIdentityChangesWhenDestinationUserChanges() {
        GmailApiDestinationTarget first = gmailTarget("user-gmail:7", "me");
        GmailApiDestinationTarget second = gmailTarget("user-gmail:7", "other-user");

        assertNotEquals(
                DestinationIdentityKeys.forTarget(first),
                DestinationIdentityKeys.forTarget(second));
    }

    @Test
    void identicalDestinationTargetsProduceStableIdentityKeys() {
        ImapAppendDestinationTarget first = imapTarget("user-destination:7", "first@example.com", "INBOX");
        ImapAppendDestinationTarget second = imapTarget("user-destination:7", "first@example.com", "INBOX");

        assertEquals(
                DestinationIdentityKeys.forTarget(first),
                DestinationIdentityKeys.forTarget(second));
    }

    private ImapAppendDestinationTarget imapTarget(String subjectKey, String username, String folder) {
        return new ImapAppendDestinationTarget(
                subjectKey,
                7L,
                "alice",
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                username,
                "secret",
                folder);
    }

    private GmailApiDestinationTarget gmailTarget(String subjectKey, String destinationUser) {
        return new GmailApiDestinationTarget(
                subjectKey,
                7L,
                "alice",
                UserMailDestinationConfigService.PROVIDER_GMAIL,
                destinationUser,
                "client-id",
                "client-secret",
                "refresh-token",
                "https://localhost/oauth/callback",
                true,
                false,
                false);
    }
}
