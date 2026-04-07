package dev.inboxbridge.service.destination;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;

public final class DestinationIdentityKeys {

    private DestinationIdentityKeys() {
    }

    /**
     * Derives a stable mailbox identity key for dedupe and checkpoint scoping.
     *
     * <p>The identity intentionally follows the effective destination mailbox
     * configuration rather than the user-scoped destination subject key. This lets
     * InboxBridge keep history separate when the same user switches to a different
     * destination mailbox or folder, while still treating unchanged configurations
     * as the same mailbox identity across polls and restarts.
     */
    public static String forTarget(MailDestinationTarget target) {
        if (target == null) {
            return null;
        }
        if (target instanceof ImapAppendDestinationTarget imapTarget) {
            return "imap-append:" + sha256Hex(String.join("\n",
                    nullSafe(imapTarget.providerId()),
                    nullSafe(imapTarget.host()),
                    Integer.toString(imapTarget.port()),
                    Boolean.toString(imapTarget.tls()),
                    String.valueOf(imapTarget.authMethod()),
                    String.valueOf(imapTarget.oauthProvider()),
                    nullSafe(imapTarget.username()),
                    nullSafe(imapTarget.folder())));
        }
        if (target instanceof GmailApiDestinationTarget gmailTarget) {
            return "gmail-api:" + sha256Hex(String.join("\n",
                    nullSafe(gmailTarget.providerId()),
                    nullSafe(gmailTarget.clientId()),
                    nullSafe(gmailTarget.refreshToken()),
                    nullSafe(gmailTarget.destinationUser())));
        }
        return target.deliveryMode() + ":" + sha256Hex(target.subjectKey());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(nullSafe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
