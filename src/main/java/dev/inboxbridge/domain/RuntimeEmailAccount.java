package dev.inboxbridge.domain;

import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;

public record RuntimeEmailAccount(
        String id,
        String ownerKind,
        Long ownerUserId,
        String ownerUsername,
        boolean enabled,
        InboxBridgeConfig.Protocol protocol,
        String host,
        int port,
        boolean tls,
        InboxBridgeConfig.AuthMethod authMethod,
        InboxBridgeConfig.OAuthProvider oauthProvider,
        String username,
        String password,
        String oauthRefreshToken,
        Optional<String> folder,
        boolean unreadOnly,
        Optional<String> customLabel,
        MailDestinationTarget destination) {
}
