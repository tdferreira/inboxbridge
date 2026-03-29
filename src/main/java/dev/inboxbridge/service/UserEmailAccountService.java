package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.AdminPollEventSummary;
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

    public List<UserEmailAccountView> listForUser(Long userId) {
        Map<String, ImportStats> importStatsBySource = importStatsBySource();
        List<UserEmailAccount> bridges = repository.listByUserId(userId);
        Map<String, dev.inboxbridge.dto.SourcePollingStateView> pollingStateBySource = sourcePollingStateService.viewBySourceIds(
                bridges.stream()
                        .map(bridge -> bridge.bridgeId)
                        .toList());
        return bridges.stream()
                .map(bridge -> toView(
                        bridge,
                        importStatsBySource.getOrDefault(bridge.bridgeId, ImportStats.EMPTY),
                        pollingStateBySource.get(bridge.bridgeId)))
                .toList();
    }

    public List<UserEmailAccount> listEnabledBridges() {
        return repository.list("enabled", true);
    }

    public Optional<UserEmailAccount> findByBridgeId(String bridgeId) {
        return repository.findByBridgeId(bridgeId);
    }

    public EmailAccountConnectionTestResult testConnection(AppUser user, UpdateUserEmailAccountRequest request) {
        RuntimeEmailAccount candidate = preview(user, request);
        return mailSourceClient.testConnection(candidate);
    }

    public RuntimeEmailAccount preview(AppUser user, UpdateUserEmailAccountRequest request) {
        String bridgeId = requireNonBlank(request.bridgeId(), "Mail fetcher ID");
        UserEmailAccount existing = resolveExistingBridge(user, request);
        InboxBridgeConfig.Protocol protocol = parseProtocol(request.protocol());
        String host = requireNonBlank(request.host(), "Host");
        InboxBridgeConfig.AuthMethod authMethod = parseAuthMethod(request.authMethod());
        InboxBridgeConfig.OAuthProvider oauthProvider = parseOAuthProvider(request.oauthProvider());
        if (requiresMicrosoftOAuth(host)) {
            authMethod = InboxBridgeConfig.AuthMethod.OAUTH2;
            oauthProvider = InboxBridgeConfig.OAuthProvider.MICROSOFT;
        }
        return new RuntimeEmailAccount(
                bridgeId,
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
    }

    @Transactional
    public UserEmailAccountView upsert(AppUser user, UpdateUserEmailAccountRequest request) {
        if (!secretEncryptionService.isConfigured()) {
            throw new IllegalStateException("Secure secret storage must be configured before storing user bridge secrets in the database.");
        }
        String bridgeId = requireNonBlank(request.bridgeId(), "Mail fetcher ID");
        UserEmailAccount existing = resolveExistingBridge(user, request);
        UserEmailAccount bridge = existing == null ? new UserEmailAccount() : existing;
        // Fetcher IDs are global across env-backed runtime config, OAuth state,
        // logs, and imported-message attribution, so renames must stay unique.
        repository.findByBridgeId(bridgeId)
                .filter(candidate -> bridge.id == null || !candidate.id.equals(bridge.id))
                .ifPresent(candidate -> {
                    throw new IllegalArgumentException("Mail fetcher ID already exists");
                });
        boolean collidesWithSystemSource = envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .anyMatch(source -> source.id().equals(bridgeId));
        if (collidesWithSystemSource) {
            throw new IllegalArgumentException("Mail fetcher ID already exists");
        }
        boolean isNew = bridge.id == null;
        String host = requireNonBlank(request.host(), "Host");
        InboxBridgeConfig.AuthMethod authMethod = parseAuthMethod(request.authMethod());
        InboxBridgeConfig.OAuthProvider oauthProvider = parseOAuthProvider(request.oauthProvider());
        if (requiresMicrosoftOAuth(host)) {
            authMethod = InboxBridgeConfig.AuthMethod.OAUTH2;
            oauthProvider = InboxBridgeConfig.OAuthProvider.MICROSOFT;
        }
        bridge.userId = user.id;
        bridge.bridgeId = bridgeId;
        bridge.enabled = request.enabled() == null || request.enabled();
        bridge.protocol = parseProtocol(request.protocol());
        bridge.host = host;
        bridge.port = request.port() == null ? defaultPort(bridge.protocol) : request.port();
        bridge.tls = request.tls() == null || request.tls();
        bridge.authMethod = authMethod;
        bridge.oauthProvider = oauthProvider;
        bridge.username = requireNonBlank(request.username(), "Username");
        bridge.folderName = blankToNull(request.folder());
        bridge.unreadOnly = request.unreadOnly() != null && request.unreadOnly();
        bridge.customLabel = blankToNull(request.customLabel());
        bridge.updatedAt = Instant.now();
        if (isNew) {
            bridge.createdAt = bridge.updatedAt;
        }

        if (request.password() != null && !request.password().isBlank()) {
            SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(
                    request.password(),
                    "user-bridge:" + user.id + ":" + bridge.bridgeId + ":password");
            bridge.passwordCiphertext = encrypted.ciphertextBase64();
            bridge.passwordNonce = encrypted.nonceBase64();
            bridge.keyVersion = secretEncryptionService.keyVersion();
        }
        if (request.oauthRefreshToken() != null && !request.oauthRefreshToken().isBlank()) {
            SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(
                    request.oauthRefreshToken(),
                    "user-bridge:" + user.id + ":" + bridge.bridgeId + ":oauth-refresh-token");
            bridge.oauthRefreshTokenCiphertext = encrypted.ciphertextBase64();
            bridge.oauthRefreshTokenNonce = encrypted.nonceBase64();
            bridge.keyVersion = secretEncryptionService.keyVersion();
        }

        repository.persist(bridge);
        return toView(
                bridge,
                ImportStats.EMPTY,
                sourcePollEventState(bridge.bridgeId));
    }

    @Transactional
    public void delete(AppUser user, String bridgeId) {
        UserEmailAccount bridge = repository.findByBridgeId(bridgeId)
                .filter(existing -> existing.userId.equals(user.id))
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        repository.delete(bridge);
    }

    public String decryptPassword(UserEmailAccount bridge) {
        if (bridge.passwordCiphertext == null || bridge.passwordNonce == null) {
            return "";
        }
        return secretEncryptionService.decrypt(
                bridge.passwordCiphertext,
                bridge.passwordNonce,
                bridge.keyVersion,
                "user-bridge:" + bridge.userId + ":" + bridge.bridgeId + ":password");
    }

    public String decryptRefreshToken(UserEmailAccount bridge) {
        if (bridge.oauthRefreshTokenCiphertext == null || bridge.oauthRefreshTokenNonce == null) {
            return fallbackStoredRefreshToken(bridge).orElse("");
        }
        return secretEncryptionService.decrypt(
                bridge.oauthRefreshTokenCiphertext,
                bridge.oauthRefreshTokenNonce,
                bridge.keyVersion,
                "user-bridge:" + bridge.userId + ":" + bridge.bridgeId + ":oauth-refresh-token");
    }

    private UserEmailAccountView toView(
            UserEmailAccount bridge,
            ImportStats importStats,
            dev.inboxbridge.dto.SourcePollingStateView pollingState) {
        PollingSettingsService.EffectivePollingSettings effectiveSettings = sourcePollingSettingsService.effectiveSettingsFor(
                new dev.inboxbridge.domain.RuntimeEmailAccount(
                        bridge.bridgeId,
                        "USER",
                        bridge.userId,
                        null,
                        bridge.enabled,
                        bridge.protocol,
                        bridge.host,
                        bridge.port,
                        bridge.tls,
                        bridge.authMethod,
                        bridge.oauthProvider,
                        bridge.username,
                        decryptPassword(bridge),
                        decryptRefreshToken(bridge),
                        Optional.ofNullable(bridge.folderName),
                        bridge.unreadOnly,
                        Optional.ofNullable(bridge.customLabel),
                        null));
        AdminPollEventSummary lastEvent = sourcePollEventService.latestForSource(bridge.bridgeId).orElse(null);
        dev.inboxbridge.dto.SourcePollingStateView sanitizedPollingState = sanitizePollingState(bridge, pollingState);
        return new UserEmailAccountView(
                bridge.bridgeId,
                bridge.enabled,
                effectiveSettings.pollEnabled(),
                effectiveSettings.pollIntervalText(),
                effectiveSettings.fetchWindow(),
                bridge.protocol.name(),
                bridge.host,
                bridge.port,
                bridge.tls,
                bridge.authMethod.name(),
                bridge.oauthProvider.name(),
                bridge.username,
                bridge.passwordCiphertext != null,
                hasEffectiveOAuthRefreshToken(bridge),
                bridge.folderName == null ? "INBOX" : bridge.folderName,
                bridge.unreadOnly,
                bridge.customLabel == null ? "" : bridge.customLabel,
                tokenStorageMode(bridge),
                importStats.totalImported(),
                importStats.lastImportedAt(),
                sanitizeLastEvent(bridge, lastEvent),
                sanitizedPollingState);
    }

    private dev.inboxbridge.dto.SourcePollingStateView sourcePollEventState(String bridgeId) {
        return sourcePollingStateService.viewForSource(bridgeId).orElse(null);
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

    private String tokenStorageMode(UserEmailAccount bridge) {
        if (bridge.authMethod == InboxBridgeConfig.AuthMethod.PASSWORD) {
            return "PASSWORD";
        }
        if (bridge.oauthProvider == InboxBridgeConfig.OAuthProvider.NONE) {
            return "NOT_CONFIGURED";
        }
        return hasEffectiveOAuthRefreshToken(bridge) ? "DATABASE" : "NOT_CONFIGURED";
    }

    private boolean hasEffectiveOAuthRefreshToken(UserEmailAccount bridge) {
        return bridge.oauthRefreshTokenCiphertext != null
                || fallbackStoredRefreshToken(bridge).filter(token -> !token.isBlank()).isPresent();
    }

    private Optional<String> fallbackStoredRefreshToken(UserEmailAccount bridge) {
        return switch (bridge.oauthProvider) {
            case MICROSOFT -> fallbackMicrosoftRefreshToken(bridge);
            case GOOGLE -> fallbackGoogleRefreshToken(bridge);
            default -> Optional.empty();
        };
    }

    private Optional<String> fallbackMicrosoftRefreshToken(UserEmailAccount bridge) {
        if (bridge.oauthProvider != InboxBridgeConfig.OAuthProvider.MICROSOFT || !oAuthCredentialService.secureStorageConfigured()) {
            return Optional.empty();
        }
        return oAuthCredentialService.findMicrosoftCredential(bridge.bridgeId)
                .map(OAuthCredentialService.StoredOAuthCredential::refreshToken)
                .filter(token -> token != null && !token.isBlank());
    }

    private Optional<String> fallbackGoogleRefreshToken(UserEmailAccount bridge) {
        if (bridge.oauthProvider != InboxBridgeConfig.OAuthProvider.GOOGLE || !oAuthCredentialService.secureStorageConfigured()) {
            return Optional.empty();
        }
        return oAuthCredentialService.findGoogleCredential("source-google:" + bridge.bridgeId)
                .map(OAuthCredentialService.StoredOAuthCredential::refreshToken)
                .filter(token -> token != null && !token.isBlank());
    }

    private AdminPollEventSummary sanitizeLastEvent(UserEmailAccount bridge, AdminPollEventSummary lastEvent) {
        if (lastEvent == null || !"ERROR".equals(lastEvent.status()) || lastEvent.error() == null) {
            return lastEvent;
        }
        if (lastEvent.error().contains("configured for OAuth2 but has no refresh token")) {
            OAuthCredentialService.StoredOAuthCredential credential = oAuthCredentialService.findMicrosoftCredential(bridge.bridgeId).orElse(null);
            if (credential != null && credential.updatedAt() != null && lastEvent.finishedAt() != null
                    && credential.updatedAt().isAfter(lastEvent.finishedAt())) {
                return null;
            }
        }
        if (shouldReplaceWithRevokedGmailMessage(bridge.userId, lastEvent.error(), lastEvent.finishedAt())) {
            return new AdminPollEventSummary(
                    lastEvent.sourceId(),
                    lastEvent.trigger(),
                    lastEvent.status(),
                    lastEvent.startedAt(),
                    lastEvent.finishedAt(),
                    lastEvent.fetched(),
                    lastEvent.imported(),
                    lastEvent.duplicates(),
                    sourcePrefixedRevokedGmailAccessMessage(bridge.bridgeId));
        }
        return lastEvent;
    }

    private dev.inboxbridge.dto.SourcePollingStateView sanitizePollingState(
            UserEmailAccount bridge,
            dev.inboxbridge.dto.SourcePollingStateView pollingState) {
        if (pollingState == null) {
            return null;
        }
        if (!shouldReplaceWithRevokedGmailMessage(bridge.userId, pollingState.lastFailureReason(), pollingState.lastFailureAt())) {
            return pollingState;
        }
        return new dev.inboxbridge.dto.SourcePollingStateView(
                pollingState.nextPollAt(),
                pollingState.cooldownUntil(),
                pollingState.consecutiveFailures(),
                sourcePrefixedRevokedGmailAccessMessage(bridge.bridgeId),
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

    private String sourcePrefixedRevokedGmailAccessMessage(String bridgeId) {
        return "Source " + bridgeId + " failed: " + REVOKED_GMAIL_ACCESS_MESSAGE;
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
        String originalBridgeId = blankToNull(request.originalBridgeId());
        if (originalBridgeId == null) {
            return null;
        }
        return repository.findByBridgeId(originalBridgeId)
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
