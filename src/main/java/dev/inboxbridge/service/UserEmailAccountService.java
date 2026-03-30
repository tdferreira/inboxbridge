package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.DestinationMailboxFolderOptionsView;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.dto.UpdateUserEmailAccountRequest;
import dev.inboxbridge.dto.UserEmailAccountView;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserEmailAccountService {

        private static final String REVOKED_GMAIL_ACCESS_MESSAGE =
            "The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Destination Mailbox.";

    @Inject
    UserEmailAccountRepository repository;

    @Inject
    UserGmailConfigRepository userGmailConfigRepository;

    @Inject
    SecretEncryptionService secretEncryptionService;

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    SourcePollEventService sourcePollEventService;

    @Inject
    UserPollingSettingsService userPollingSettingsService;

    @Inject
    SourcePollingSettingsService sourcePollingSettingsService;

    @Inject
    SourcePollingStateService sourcePollingStateService;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    EnvSourceService envSourceService;

    @Inject
    MailSourceClient mailSourceClient;

    @Inject
    MailboxConflictService mailboxConflictService;

    public List<UserEmailAccountView> listForUser(Long userId) {
        Map<String, ImportStats> importStatsBySource = importStatsBySource();
        List<UserEmailAccount> emailAccounts = repository.listByUserId(userId);
        Map<String, dev.inboxbridge.dto.SourcePollingStateView> pollingStateBySource = sourcePollingStateService.viewBySourceIds(
                emailAccounts.stream()
                        .map(emailAccount -> emailAccount.emailAccountId)
                        .toList());
        return emailAccounts.stream()
                .map(emailAccount -> toView(
                        emailAccount,
                        importStatsBySource.getOrDefault(emailAccount.emailAccountId, ImportStats.EMPTY),
                        pollingStateBySource.get(emailAccount.emailAccountId)))
                .toList();
    }

    public List<UserEmailAccount> listEnabledBridges() {
        return repository.list("enabled", true);
    }

    public Optional<UserEmailAccount> findByEmailAccountId(String emailAccountId) {
        return repository.findByEmailAccountId(emailAccountId);
    }

    public EmailAccountConnectionTestResult testConnection(AppUser user, UpdateUserEmailAccountRequest request) {
        RuntimeEmailAccount candidate = preview(user, request);
        return mailSourceClient.testConnection(candidate);
    }

    public DestinationMailboxFolderOptionsView listFolders(AppUser user, UpdateUserEmailAccountRequest request) {
        RuntimeEmailAccount candidate = preview(user, request);
        if (candidate.protocol() != InboxBridgeConfig.Protocol.IMAP) {
            return new DestinationMailboxFolderOptionsView(List.of());
        }
        return new DestinationMailboxFolderOptionsView(mailSourceClient.listFolders(candidate));
    }

    public RuntimeEmailAccount preview(AppUser user, UpdateUserEmailAccountRequest request) {
        String emailAccountId = requireNonBlank(request.emailAccountId(), "Mail fetcher ID");
        UserEmailAccount existing = resolveExistingBridge(user, request);
        InboxBridgeConfig.Protocol protocol = parseProtocol(request.protocol());
        String host = requireNonBlank(request.host(), "Host");
        InboxBridgeConfig.AuthMethod authMethod = parseAuthMethod(request.authMethod());
        InboxBridgeConfig.OAuthProvider oauthProvider = parseOAuthProvider(request.oauthProvider());
        if (requiresMicrosoftOAuth(host)) {
            authMethod = InboxBridgeConfig.AuthMethod.OAUTH2;
            oauthProvider = InboxBridgeConfig.OAuthProvider.MICROSOFT;
        }
        RuntimeEmailAccount candidate = new RuntimeEmailAccount(
                emailAccountId,
                "USER",
                user.id,
                user.username,
                request.enabled() == null || request.enabled(),
                protocol,
                host,
                request.port() == null ? defaultPort(protocol) : request.port(),
                request.tls() == null || request.tls(),
                authMethod,
                oauthProvider,
                requireNonBlank(request.username(), "Username"),
                resolvePassword(existing, authMethod, request.password()),
                resolveRefreshToken(existing, authMethod, oauthProvider, request.oauthRefreshToken()),
                Optional.ofNullable(blankToNull(request.folder())),
                request.unreadOnly() != null && request.unreadOnly(),
                Optional.ofNullable(blankToNull(request.customLabel())),
                null);
        if (candidate.enabled() && mailboxConflictService.conflictsWithCurrentDestination(user.id, candidate)) {
            throw new IllegalArgumentException(MailboxConflictService.SOURCE_DESTINATION_CONFLICT_MESSAGE);
        }
        return candidate;
    }

    @Transactional
    public UserEmailAccountView upsert(AppUser user, UpdateUserEmailAccountRequest request) {
        if (!secretEncryptionService.isConfigured()) {
            throw new IllegalStateException("Secure secret storage must be configured before storing user email account secrets in the database.");
        }
        String emailAccountId = requireNonBlank(request.emailAccountId(), "Mail fetcher ID");
        UserEmailAccount existing = resolveExistingBridge(user, request);
        UserEmailAccount emailAccount = existing == null ? new UserEmailAccount() : existing;
        // Fetcher IDs are global across env-backed runtime config, OAuth state,
        // logs, and imported-message attribution, so renames must stay unique.
        repository.findByEmailAccountId(emailAccountId)
                .filter(candidate -> emailAccount.id == null || !candidate.id.equals(emailAccount.id))
                .ifPresent(candidate -> {
                    throw new IllegalArgumentException("Mail fetcher ID already exists");
                });
        boolean collidesWithSystemSource = envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .anyMatch(source -> source.id().equals(emailAccountId));
        if (collidesWithSystemSource) {
            throw new IllegalArgumentException("Mail fetcher ID already exists");
        }
        boolean isNew = emailAccount.id == null;
        String host = requireNonBlank(request.host(), "Host");
        InboxBridgeConfig.AuthMethod authMethod = parseAuthMethod(request.authMethod());
        InboxBridgeConfig.OAuthProvider oauthProvider = parseOAuthProvider(request.oauthProvider());
        if (requiresMicrosoftOAuth(host)) {
            authMethod = InboxBridgeConfig.AuthMethod.OAUTH2;
            oauthProvider = InboxBridgeConfig.OAuthProvider.MICROSOFT;
        }
        emailAccount.userId = user.id;
        emailAccount.emailAccountId = emailAccountId;
        emailAccount.enabled = request.enabled() == null || request.enabled();
        emailAccount.protocol = parseProtocol(request.protocol());
        emailAccount.host = host;
        emailAccount.port = request.port() == null ? defaultPort(emailAccount.protocol) : request.port();
        emailAccount.tls = request.tls() == null || request.tls();
        emailAccount.authMethod = authMethod;
        emailAccount.oauthProvider = oauthProvider;
        emailAccount.username = requireNonBlank(request.username(), "Username");
        emailAccount.folderName = blankToNull(request.folder());
        emailAccount.unreadOnly = request.unreadOnly() != null && request.unreadOnly();
        emailAccount.customLabel = blankToNull(request.customLabel());
        emailAccount.updatedAt = Instant.now();
        if (isNew) {
            emailAccount.createdAt = emailAccount.updatedAt;
        }

        RuntimeEmailAccount candidate = new RuntimeEmailAccount(
                emailAccount.emailAccountId,
                "USER",
                user.id,
                user.username,
                emailAccount.enabled,
                emailAccount.protocol,
                emailAccount.host,
                emailAccount.port,
                emailAccount.tls,
                emailAccount.authMethod,
                emailAccount.oauthProvider,
                emailAccount.username,
                emailAccount.authMethod == InboxBridgeConfig.AuthMethod.PASSWORD ? resolvePassword(existing, emailAccount.authMethod, request.password()) : "",
                emailAccount.authMethod == InboxBridgeConfig.AuthMethod.OAUTH2 ? resolveRefreshToken(existing, emailAccount.authMethod, emailAccount.oauthProvider, request.oauthRefreshToken()) : "",
                Optional.ofNullable(blankToNull(request.folder())),
                emailAccount.unreadOnly,
                Optional.ofNullable(emailAccount.customLabel),
                null);
        if (candidate.enabled() && mailboxConflictService.conflictsWithCurrentDestination(user.id, candidate)) {
            throw new IllegalArgumentException(MailboxConflictService.SOURCE_DESTINATION_CONFLICT_MESSAGE);
        }

        if (request.password() != null && !request.password().isBlank()) {
            SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(
                    request.password(),
                    "user-bridge:" + user.id + ":" + emailAccount.emailAccountId + ":password");
            emailAccount.passwordCiphertext = encrypted.ciphertextBase64();
            emailAccount.passwordNonce = encrypted.nonceBase64();
            emailAccount.keyVersion = secretEncryptionService.keyVersion();
        }
        if (request.oauthRefreshToken() != null && !request.oauthRefreshToken().isBlank()) {
            SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(
                    request.oauthRefreshToken(),
                    "user-bridge:" + user.id + ":" + emailAccount.emailAccountId + ":oauth-refresh-token");
            emailAccount.oauthRefreshTokenCiphertext = encrypted.ciphertextBase64();
            emailAccount.oauthRefreshTokenNonce = encrypted.nonceBase64();
            emailAccount.keyVersion = secretEncryptionService.keyVersion();
        }

        repository.persist(emailAccount);
        return toView(
                emailAccount,
                ImportStats.EMPTY,
                sourcePollEventState(emailAccount.emailAccountId));
    }

    @Transactional
    public void delete(AppUser user, String emailAccountId) {
        UserEmailAccount bridge = repository.findByEmailAccountId(emailAccountId)
                .filter(existing -> existing.userId.equals(user.id))
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        repository.delete(bridge);
    }

    public String decryptPassword(UserEmailAccount emailAccount) {
        if (emailAccount.passwordCiphertext == null || emailAccount.passwordNonce == null) {
            return "";
        }
        return secretEncryptionService.decrypt(
                emailAccount.passwordCiphertext,
                emailAccount.passwordNonce,
                emailAccount.keyVersion,
                "user-bridge:" + emailAccount.userId + ":" + emailAccount.emailAccountId + ":password");
    }

    public String decryptRefreshToken(UserEmailAccount emailAccount) {
        if (emailAccount.oauthRefreshTokenCiphertext == null || emailAccount.oauthRefreshTokenNonce == null) {
            return fallbackStoredRefreshToken(emailAccount).orElse("");
        }
        return secretEncryptionService.decrypt(
                emailAccount.oauthRefreshTokenCiphertext,
                emailAccount.oauthRefreshTokenNonce,
                emailAccount.keyVersion,
                "user-bridge:" + emailAccount.userId + ":" + emailAccount.emailAccountId + ":oauth-refresh-token");
    }

    private UserEmailAccountView toView(
            UserEmailAccount emailAccount,
            ImportStats importStats,
            dev.inboxbridge.dto.SourcePollingStateView pollingState) {
        PollingSettingsService.EffectivePollingSettings effectiveSettings = sourcePollingSettingsService.effectiveSettingsFor(
                new dev.inboxbridge.domain.RuntimeEmailAccount(
                        emailAccount.emailAccountId,
                        "USER",
                        emailAccount.userId,
                        null,
                        emailAccount.enabled,
                        emailAccount.protocol,
                        emailAccount.host,
                        emailAccount.port,
                        emailAccount.tls,
                        emailAccount.authMethod,
                        emailAccount.oauthProvider,
                        emailAccount.username,
                        decryptPassword(emailAccount),
                        decryptRefreshToken(emailAccount),
                        Optional.ofNullable(emailAccount.folderName),
                        emailAccount.unreadOnly,
                        Optional.ofNullable(emailAccount.customLabel),
                        null));
        AdminPollEventSummary lastEvent = sourcePollEventService.latestForSource(emailAccount.emailAccountId).orElse(null);
        dev.inboxbridge.dto.SourcePollingStateView sanitizedPollingState = sanitizePollingState(emailAccount, pollingState);
        return new UserEmailAccountView(
                emailAccount.emailAccountId,
                emailAccount.enabled,
                effectiveSettings.pollEnabled(),
                effectiveSettings.pollIntervalText(),
                effectiveSettings.fetchWindow(),
                emailAccount.protocol.name(),
                emailAccount.host,
                emailAccount.port,
                emailAccount.tls,
                emailAccount.authMethod.name(),
                emailAccount.oauthProvider.name(),
                emailAccount.username,
                emailAccount.passwordCiphertext != null,
                hasEffectiveOAuthRefreshToken(emailAccount),
                emailAccount.folderName == null ? "INBOX" : emailAccount.folderName,
                emailAccount.unreadOnly,
                emailAccount.customLabel == null ? "" : emailAccount.customLabel,
                tokenStorageMode(emailAccount),
                importStats.totalImported(),
                importStats.lastImportedAt(),
                sanitizeLastEvent(emailAccount, lastEvent),
                sanitizedPollingState);
    }

    private dev.inboxbridge.dto.SourcePollingStateView sourcePollEventState(String emailAccountId) {
        return sourcePollingStateService.viewForSource(emailAccountId).orElse(null);
    }

    private Map<String, ImportStats> importStatsBySource() {
        Map<String, ImportStats> importStatsBySource = new HashMap<>();
        for (Object[] row : importedMessageRepository.summarizeBySource()) {
            importStatsBySource.put(
                    (String) row[0],
                    new ImportStats(((Long) row[1]).longValue(), (Instant) row[2]));
        }
        return importStatsBySource;
    }

    private String tokenStorageMode(UserEmailAccount emailAccount) {
        if (emailAccount.authMethod == InboxBridgeConfig.AuthMethod.PASSWORD) {
            return "PASSWORD";
        }
        if (emailAccount.oauthProvider == InboxBridgeConfig.OAuthProvider.NONE) {
            return "NOT_CONFIGURED";
        }
        return hasEffectiveOAuthRefreshToken(emailAccount) ? "DATABASE" : "NOT_CONFIGURED";
    }

    private boolean hasEffectiveOAuthRefreshToken(UserEmailAccount emailAccount) {
        return emailAccount.oauthRefreshTokenCiphertext != null
                || fallbackStoredRefreshToken(emailAccount).filter(token -> !token.isBlank()).isPresent();
    }

    private Optional<String> fallbackStoredRefreshToken(UserEmailAccount emailAccount) {
        return switch (emailAccount.oauthProvider) {
            case MICROSOFT -> fallbackMicrosoftRefreshToken(emailAccount);
            case GOOGLE -> fallbackGoogleRefreshToken(emailAccount);
            default -> Optional.empty();
        };
    }

    private Optional<String> fallbackMicrosoftRefreshToken(UserEmailAccount emailAccount) {
        if (emailAccount.oauthProvider != InboxBridgeConfig.OAuthProvider.MICROSOFT || !oAuthCredentialService.secureStorageConfigured()) {
            return Optional.empty();
        }
        return oAuthCredentialService.findMicrosoftCredential(emailAccount.emailAccountId)
                .map(OAuthCredentialService.StoredOAuthCredential::refreshToken)
                .filter(token -> token != null && !token.isBlank());
    }

    private Optional<String> fallbackGoogleRefreshToken(UserEmailAccount emailAccount) {
        if (emailAccount.oauthProvider != InboxBridgeConfig.OAuthProvider.GOOGLE || !oAuthCredentialService.secureStorageConfigured()) {
            return Optional.empty();
        }
        return oAuthCredentialService.findGoogleCredential("source-google:" + emailAccount.emailAccountId)
                .map(OAuthCredentialService.StoredOAuthCredential::refreshToken)
                .filter(token -> token != null && !token.isBlank());
    }

    private AdminPollEventSummary sanitizeLastEvent(UserEmailAccount emailAccount, AdminPollEventSummary lastEvent) {
        if (lastEvent == null || !"ERROR".equals(lastEvent.status()) || lastEvent.error() == null) {
            return lastEvent;
        }
        if (lastEvent.error().contains("configured for OAuth2 but has no refresh token")) {
            OAuthCredentialService.StoredOAuthCredential credential = oAuthCredentialService.findMicrosoftCredential(emailAccount.emailAccountId).orElse(null);
            if (credential != null && credential.updatedAt() != null && lastEvent.finishedAt() != null
                    && credential.updatedAt().isAfter(lastEvent.finishedAt())) {
                return null;
            }
        }
        if (shouldReplaceWithRevokedGmailMessage(emailAccount.userId, lastEvent.error(), lastEvent.finishedAt())) {
            return new AdminPollEventSummary(
                    lastEvent.sourceId(),
                    lastEvent.trigger(),
                    lastEvent.status(),
                    lastEvent.startedAt(),
                    lastEvent.finishedAt(),
                    lastEvent.fetched(),
                    lastEvent.imported(),
                    lastEvent.duplicates(),
                    sourcePrefixedRevokedGmailAccessMessage(emailAccount.emailAccountId));
        }
        return lastEvent;
    }

    private dev.inboxbridge.dto.SourcePollingStateView sanitizePollingState(
            UserEmailAccount emailAccount,
            dev.inboxbridge.dto.SourcePollingStateView pollingState) {
        if (pollingState == null) {
            return null;
        }
        if (!shouldReplaceWithRevokedGmailMessage(emailAccount.userId, pollingState.lastFailureReason(), pollingState.lastFailureAt())) {
            return pollingState;
        }
        return new dev.inboxbridge.dto.SourcePollingStateView(
                pollingState.nextPollAt(),
                pollingState.cooldownUntil(),
                pollingState.consecutiveFailures(),
                sourcePrefixedRevokedGmailAccessMessage(emailAccount.emailAccountId),
                pollingState.lastFailureAt(),
                pollingState.lastSuccessAt());
    }

    private boolean shouldReplaceWithRevokedGmailMessage(Long userId, String errorMessage, Instant referenceTime) {
        if (userId == null || errorMessage == null || !looksLikeRevokedOrStaleGmailAccessError(errorMessage)) {
            return false;
        }
        if (gmailAccountCurrentlyLinked(userId)) {
            return false;
        }
        Instant gmailConfigUpdatedAt = userGmailConfigRepository.findByUserId(userId)
                .map(config -> config.updatedAt)
                .orElse(null);
        return referenceTime == null
                || gmailConfigUpdatedAt == null
                || !gmailConfigUpdatedAt.isBefore(referenceTime);
    }

    private boolean gmailAccountCurrentlyLinked(Long userId) {
        boolean storedRefreshToken = userGmailConfigRepository.findByUserId(userId)
                .map(config -> config.refreshTokenCiphertext != null && config.refreshTokenNonce != null)
                .orElse(false);
        if (storedRefreshToken) {
            return true;
        }
        return oAuthCredentialService.findGoogleCredential("user-gmail:" + userId)
                .map(credential -> credential.refreshToken() != null && !credential.refreshToken().isBlank())
                .orElse(false);
    }

    private boolean looksLikeRevokedOrStaleGmailAccessError(String errorMessage) {
        return errorMessage.contains("Failed to list Gmail labels: 401")
                || errorMessage.contains("Failed to import Gmail message: 401")
                || errorMessage.contains("Invalid authentication credentials")
                || errorMessage.contains("no longer grants InboxBridge access");
    }

    private String sourcePrefixedRevokedGmailAccessMessage(String emailAccountId) {
        return "Source " + emailAccountId + " failed: " + REVOKED_GMAIL_ACCESS_MESSAGE;
    }

    private InboxBridgeConfig.Protocol parseProtocol(String value) {
        return value == null || value.isBlank() ? InboxBridgeConfig.Protocol.IMAP : InboxBridgeConfig.Protocol.valueOf(value.toUpperCase());
    }

    private InboxBridgeConfig.AuthMethod parseAuthMethod(String value) {
        return value == null || value.isBlank() ? InboxBridgeConfig.AuthMethod.PASSWORD : InboxBridgeConfig.AuthMethod.valueOf(value.toUpperCase());
    }

    private InboxBridgeConfig.OAuthProvider parseOAuthProvider(String value) {
        return value == null || value.isBlank() ? InboxBridgeConfig.OAuthProvider.NONE : InboxBridgeConfig.OAuthProvider.valueOf(value.toUpperCase());
    }

    private int defaultPort(InboxBridgeConfig.Protocol protocol) {
        return protocol == InboxBridgeConfig.Protocol.IMAP ? 993 : 995;
    }

    private boolean requiresMicrosoftOAuth(String host) {
        String normalizedHost = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return "outlook.office365.com".equals(normalizedHost)
                || "imap-mail.outlook.com".equals(normalizedHost)
                || "pop-mail.outlook.com".equals(normalizedHost);
    }

    private UserEmailAccount resolveExistingBridge(AppUser user, UpdateUserEmailAccountRequest request) {
        String originalEmailAccountId = blankToNull(request.originalEmailAccountId());
        if (originalEmailAccountId == null) {
            return null;
        }
        return repository.findByEmailAccountId(originalEmailAccountId)
                .filter(existing -> existing.userId.equals(user.id))
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
    }

    private String resolvePassword(UserEmailAccount existing, InboxBridgeConfig.AuthMethod authMethod, String password) {
        if (authMethod != InboxBridgeConfig.AuthMethod.PASSWORD) {
            return "";
        }
        if (password != null && !password.isBlank()) {
            return password;
        }
        if (existing != null) {
            String stored = decryptPassword(existing);
            if (!stored.isBlank()) {
                return stored;
            }
        }
        throw new IllegalArgumentException("Password is required");
    }

    private String resolveRefreshToken(
            UserEmailAccount existing,
            InboxBridgeConfig.AuthMethod authMethod,
            InboxBridgeConfig.OAuthProvider oauthProvider,
            String oauthRefreshToken) {
        if (authMethod != InboxBridgeConfig.AuthMethod.OAUTH2 || oauthProvider == InboxBridgeConfig.OAuthProvider.NONE) {
            return "";
        }
        if (oauthRefreshToken != null && !oauthRefreshToken.isBlank()) {
            return oauthRefreshToken;
        }
        if (existing != null) {
            String stored = decryptRefreshToken(existing);
            if (!stored.isBlank()) {
                return stored;
            }
        }
        throw new IllegalArgumentException("OAuth refresh token is required or connect provider OAuth first");
    }

    private String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ImportStats(long totalImported, Instant lastImportedAt) {
        private static final ImportStats EMPTY = new ImportStats(0, null);
    }
}
