package dev.inboxbridge.domain;

import dev.inboxbridge.config.InboxBridgeConfig;

public record ImapAppendDestinationTarget(
        String subjectKey,
        Long userId,
        String ownerUsername,
        String providerId,
        String host,
        int port,
        boolean tls,
        InboxBridgeConfig.AuthMethod authMethod,
        InboxBridgeConfig.OAuthProvider oauthProvider,
        String username,
        String password,
        String folder,
        String spamJunkFolder) implements MailDestinationTarget {

    public ImapAppendDestinationTarget(
            String subjectKey,
            Long userId,
            String ownerUsername,
            String providerId,
            String host,
            int port,
            boolean tls,
            InboxBridgeConfig.AuthMethod authMethod,
            InboxBridgeConfig.OAuthProvider oauthProvider,
            String username,
            String password,
            String folder) {
        this(
                subjectKey,
                userId,
                ownerUsername,
                providerId,
                host,
                port,
                tls,
                authMethod,
                oauthProvider,
                username,
                password,
                folder,
                null);
    }

    @Override
    public String deliveryMode() {
        return "IMAP_APPEND";
    }
}
