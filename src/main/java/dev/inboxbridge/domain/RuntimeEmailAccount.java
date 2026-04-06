package dev.inboxbridge.domain;

import java.util.List;
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
        SourceFetchMode fetchMode,
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
                SourceFetchMode.POLLING,
                customLabel,
                SourcePostPollSettings.none(),
                destination);
    }

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
            SourcePostPollSettings postPollSettings,
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
                SourceFetchMode.POLLING,
                customLabel,
                postPollSettings,
                destination);
    }

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
            SourceFetchMode fetchMode,
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
                fetchMode,
                customLabel,
                SourcePostPollSettings.none(),
                destination);
    }

    /**
     * Returns the normalized source mailbox folders for this account.
     *
     * <p>IMAP accounts may resolve to multiple configured folders, while POP3
     * always resolves to the single INBOX maildrop.
     */
    public List<String> sourceFolders() {
        return SourceMailboxFolders.forSource(protocol, folder);
    }

    public String primaryFolder() {
        return SourceMailboxFolders.primary(protocol, folder);
    }
}
