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
        SourcePostPollSettings postPollSettings,
        MailDestinationTarget destination) {

    public RuntimeEmailAccount(
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
        this(
                id,
                ownerKind,
                ownerUserId,
                ownerUsername,
                enabled,
                protocol,
                host,
                port,
                tls,
                authMethod,
                oauthProvider,
                username,
                password,
                oauthRefreshToken,
                folder,
                unreadOnly,
                customLabel,
                SourcePostPollSettings.none(),
                destination);
    }
}
