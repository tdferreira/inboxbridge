package dev.inboxbridge.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        Optional<String> folderLabelMappings,
        SourceSpamJunkStrategy spamJunkStrategy,
        Optional<String> spamJunkSourceFolder,
        SourcePostPollSettings postPollSettings,
        MailDestinationTarget destination) {

    public RuntimeEmailAccount {
        folder = folder == null ? Optional.empty() : folder;
        customLabel = customLabel == null ? Optional.empty() : customLabel;
        folderLabelMappings = folderLabelMappings == null ? Optional.empty() : folderLabelMappings;
        spamJunkStrategy = spamJunkStrategy == null ? SourceSpamJunkStrategy.IGNORE : spamJunkStrategy;
        spamJunkSourceFolder = spamJunkSourceFolder == null ? Optional.empty() : spamJunkSourceFolder;
        postPollSettings = postPollSettings == null ? SourcePostPollSettings.none() : postPollSettings;
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
                Optional.empty(),
                SourceSpamJunkStrategy.IGNORE,
                Optional.empty(),
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
                Optional.empty(),
                SourceSpamJunkStrategy.IGNORE,
                Optional.empty(),
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
                Optional.empty(),
                SourceSpamJunkStrategy.IGNORE,
                Optional.empty(),
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
            SourceFetchMode fetchMode,
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
                fetchMode,
                customLabel,
                Optional.empty(),
                SourceSpamJunkStrategy.IGNORE,
                Optional.empty(),
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
            Optional<String> folderLabelMappings,
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
                fetchMode,
                customLabel,
                folderLabelMappings,
                SourceSpamJunkStrategy.IGNORE,
                Optional.empty(),
                postPollSettings,
                destination);
    }

    /**
     * Returns the normalized source mailbox folders for this account.
     *
     * <p>IMAP accounts may resolve to multiple configured folders, while POP3
     * always resolves to the single INBOX maildrop.
     */
    public List<String> sourceFolders() {
        LinkedHashSet<String> folders = new LinkedHashSet<>(SourceMailboxFolders.forSource(protocol, folder));
        if (protocol == InboxBridgeConfig.Protocol.IMAP && spamJunkStrategy.importsSpamJunk()) {
            for (String spamJunkFolder : spamJunkSourceFolders()) {
                boolean alreadyPresent = folders.stream()
                        .anyMatch(existing -> MailboxFolderRoleDetector.sameFolder(existing, spamJunkFolder));
                if (!alreadyPresent) {
                    folders.add(spamJunkFolder);
                }
            }
        }
        return List.copyOf(folders);
    }

    public List<String> spamJunkSourceFolders() {
        if (spamJunkSourceFolder.isEmpty() || spamJunkSourceFolder.orElse("").isBlank()) {
            return List.of();
        }
        List<String> folders = new ArrayList<>();
        for (String token : spamJunkSourceFolder.orElse("").split("[,\\n\\r]+")) {
            String folderName = token.trim();
            if (folderName.isEmpty()) {
                continue;
            }
            boolean alreadyPresent = folders.stream()
                    .anyMatch(existing -> MailboxFolderRoleDetector.sameFolder(existing, folderName));
            if (!alreadyPresent) {
                folders.add(folderName);
            }
        }
        return List.copyOf(folders);
    }

    public String primaryFolder() {
        return SourceMailboxFolders.primary(protocol, folder);
    }

    public boolean isSpamJunkSourceFolder(Optional<String> sourceFolder) {
        return protocol == InboxBridgeConfig.Protocol.IMAP
                && spamJunkStrategy.importsSpamJunk()
                && sourceFolder.filter(folderName -> spamJunkSourceFolders().stream()
                        .anyMatch(spamFolder -> MailboxFolderRoleDetector.sameFolder(folderName, spamFolder)))
                        .isPresent();
    }

    public boolean routesSpamJunkFolder(Optional<String> sourceFolder) {
        return spamJunkStrategy.routesSpamJunk() && isSpamJunkSourceFolder(sourceFolder);
    }
}
