package dev.inboxbridge.domain;

import java.util.Optional;

import dev.inboxbridge.config.BridgeConfig;

public record RuntimeBridge(
        String id,
        String ownerKind,
        Long ownerUserId,
        String ownerUsername,
        boolean enabled,
        BridgeConfig.Protocol protocol,
        String host,
        int port,
        boolean tls,
        BridgeConfig.AuthMethod authMethod,
        BridgeConfig.OAuthProvider oauthProvider,
        String username,
        String password,
        String oauthRefreshToken,
        Optional<String> folder,
        boolean unreadOnly,
        Optional<String> customLabel,
        GmailTarget gmailTarget) {
}
