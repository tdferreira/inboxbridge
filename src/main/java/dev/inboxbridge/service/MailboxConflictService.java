package dev.inboxbridge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MailboxConflictService {

    public static final String SOURCE_DESTINATION_CONFLICT_MESSAGE =
            "A source mailbox cannot be the same as My Destination Mailbox. Choose a different mailbox or keep that source disabled.";

    @Inject
    UserMailDestinationConfigRepository userMailDestinationConfigRepository;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    UserGmailConfigRepository userGmailConfigRepository;

    public boolean conflictsWithCurrentDestination(Long userId, RuntimeEmailAccount source) {
        if (userId == null || source == null) {
            return false;
        }
        Optional<MailboxIdentity> destination = currentDestinationIdentity(userId);
        if (destination.isEmpty()) {
            return false;
        }
        return destination.get().matches(sourceIdentity(source));
    }

    public List<String> disableSourcesMatchingCurrentDestination(Long userId) {
        Optional<MailboxIdentity> destination = currentDestinationIdentity(userId);
        if (destination.isEmpty()) {
            return List.of();
        }
        List<String> disabledSourceIds = new ArrayList<>();
        for (UserEmailAccount source : userEmailAccountRepository.listByUserId(userId)) {
            if (!source.enabled) {
                continue;
            }
            if (!destination.get().matches(sourceIdentity(source))) {
                continue;
            }
            source.enabled = false;
            source.updatedAt = Instant.now();
            userEmailAccountRepository.persist(source);
            disabledSourceIds.add(source.emailAccountId);
        }
        return disabledSourceIds;
    }

    private Optional<MailboxIdentity> currentDestinationIdentity(Long userId) {
        UserMailDestinationConfig destination = userMailDestinationConfigRepository.findByUserId(userId).orElse(null);
        if (destination == null || UserMailDestinationConfigService.PROVIDER_GMAIL.equals(destination.provider)) {
            return userGmailConfigRepository.findByUserId(userId)
                    .map(config -> normalizeMailbox(config.linkedMailboxAddress))
                    .filter(value -> !value.isBlank())
                    .map(MailboxIdentity::gmail);
        }
        return imapIdentity(destination.host, destination.username);
    }

    private MailboxIdentity sourceIdentity(RuntimeEmailAccount source) {
        if (looksLikeGoogleMailbox(source.host(), source.oauthProvider())) {
            return MailboxIdentity.gmail(normalizeMailbox(source.username()));
        }
        return imapIdentity(source.host(), source.username())
                .orElseGet(() -> new MailboxIdentity("IMAP", normalizeHost(source.host()), normalizeMailbox(source.username())));
    }

    private MailboxIdentity sourceIdentity(UserEmailAccount source) {
        if (looksLikeGoogleMailbox(source.host, source.oauthProvider)) {
            return MailboxIdentity.gmail(normalizeMailbox(source.username));
        }
        return imapIdentity(source.host, source.username)
                .orElseGet(() -> new MailboxIdentity("IMAP", normalizeHost(source.host), normalizeMailbox(source.username)));
    }

    private Optional<MailboxIdentity> imapIdentity(String host, String username) {
        String normalizedHost = normalizeHost(host);
        String normalizedMailbox = normalizeMailbox(username);
        if (normalizedHost.isBlank() || normalizedMailbox.isBlank()) {
            return Optional.empty();
        }
        if (looksLikeGoogleMailbox(normalizedHost, InboxBridgeConfig.OAuthProvider.GOOGLE)) {
            return Optional.of(MailboxIdentity.gmail(normalizedMailbox));
        }
        return Optional.of(new MailboxIdentity("IMAP", normalizedHost, normalizedMailbox));
    }

    private boolean looksLikeGoogleMailbox(String host, InboxBridgeConfig.OAuthProvider oauthProvider) {
        if (oauthProvider == InboxBridgeConfig.OAuthProvider.GOOGLE) {
            return true;
        }
        String normalizedHost = normalizeHost(host);
        return normalizedHost.contains("gmail") || normalizedHost.contains("googlemail");
    }

    private String normalizeHost(String host) {
        return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMailbox(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record MailboxIdentity(String kind, String host, String mailbox) {

        private static MailboxIdentity gmail(String mailbox) {
            return new MailboxIdentity("GMAIL", "", mailbox);
        }

        private boolean matches(MailboxIdentity other) {
            if (other == null || !kind.equals(other.kind)) {
                return false;
            }
            if ("GMAIL".equals(kind)) {
                return mailbox.equals(other.mailbox);
            }
            return host.equals(other.host) && mailbox.equals(other.mailbox);
        }
    }
}