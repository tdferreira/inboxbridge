package dev.inboxbridge.service.polling;

import java.util.Locale;
import java.util.Optional;

import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;

/**
 * Shared throttle-key derivation so diagnostics can read the same persisted
 * throttle buckets that the live poller updates.
 */
public final class PollThrottleKeys {

    private PollThrottleKeys() {
    }

    public static String sourceMailbox(RuntimeEmailAccount emailAccount) {
        return normalizeHost(emailAccount == null ? null : emailAccount.host())
                .map(host -> "source-host:" + host)
                .orElse(null);
    }

    public static String destination(MailDestinationTarget target) {
        if (target instanceof GmailApiDestinationTarget gmailTarget) {
            return "destination-provider:" + gmailTarget.deliveryMode().toLowerCase(Locale.ROOT);
        }
        if (target instanceof ImapAppendDestinationTarget imapTarget) {
            return normalizeHost(imapTarget.host())
                    .map(host -> "destination-host:" + host)
                    .orElse(null);
        }
        return null;
    }

    public static String destinationKind(MailDestinationTarget target) {
        if (target instanceof GmailApiDestinationTarget) {
            return "DESTINATION_PROVIDER";
        }
        return "DESTINATION_HOST";
    }

    private static Optional<String> normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(host.trim().toLowerCase(Locale.ROOT));
    }
}
