package dev.inboxbridge.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.BridgeConnectionTestResult;
import dev.inboxbridge.dto.UpdateUserBridgeRequest;
import dev.inboxbridge.dto.UserBridgeView;
import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserBridge;
import dev.inboxbridge.persistence.UserBridgeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserBridgeService {

    @Inject
    UserBridgeRepository repository;

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

    public List<UserBridgeView> listForUser(Long userId) {
        Map<String, ImportStats> importStatsBySource = importStatsBySource();
        List<UserBridge> bridges = repository.listByUserId(userId);
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

    public List<UserBridge> listEnabledBridges() {
        return repository.list("enabled", true);
    }

    public Optional<UserBridge> findByBridgeId(String bridgeId) {
        return repository.findByBridgeId(bridgeId);
    }

    public BridgeConnectionTestResult testConnection(AppUser user, UpdateUserBridgeRequest request) {
        RuntimeBridge candidate = preview(user, request);
        return mailSourceClient.testConnection(candidate);
    }

    public RuntimeBridge preview(AppUser user, UpdateUserBridgeRequest request) {
        String bridgeId = requireNonBlank(request.bridgeId(), "Mail fetcher ID");
        UserBridge existing = resolveExistingBridge(user, request);
        BridgeConfig.Protocol protocol = parseProtocol(request.protocol());
        BridgeConfig.AuthMethod authMethod = parseAuthMethod(request.authMethod());
        BridgeConfig.OAuthProvider oauthProvider = parseOAuthProvider(request.oauthProvider());
        return new RuntimeBridge(
                bridgeId,
                "USER",
                user.id,
                user.username,
                request.enabled() == null || request.enabled(),
                protocol,
                requireNonBlank(request.host(), "Host"),
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
    public UserBridgeView upsert(AppUser user, UpdateUserBridgeRequest request) {
        if (!secretEncryptionService.isConfigured()) {
            throw new IllegalStateException("Secure secret storage must be configured before storing user bridge secrets in the database.");
        }
        String bridgeId = requireNonBlank(request.bridgeId(), "Mail fetcher ID");
        UserBridge existing = resolveExistingBridge(user, request);
        UserBridge bridge = existing == null ? new UserBridge() : existing;
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
        bridge.userId = user.id;
        bridge.bridgeId = bridgeId;
        bridge.enabled = request.enabled() == null || request.enabled();
        bridge.protocol = parseProtocol(request.protocol());
        bridge.host = requireNonBlank(request.host(), "Host");
        bridge.port = request.port() == null ? defaultPort(bridge.protocol) : request.port();
        bridge.tls = request.tls() == null || request.tls();
        bridge.authMethod = parseAuthMethod(request.authMethod());
        bridge.oauthProvider = parseOAuthProvider(request.oauthProvider());
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
        UserBridge bridge = repository.findByBridgeId(bridgeId)
                .filter(existing -> existing.userId.equals(user.id))
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        repository.delete(bridge);
    }

    public String decryptPassword(UserBridge bridge) {
        if (bridge.passwordCiphertext == null || bridge.passwordNonce == null) {
            return "";
        }
        return secretEncryptionService.decrypt(
                bridge.passwordCiphertext,
                bridge.passwordNonce,
                bridge.keyVersion,
                "user-bridge:" + bridge.userId + ":" + bridge.bridgeId + ":password");
    }

    public String decryptRefreshToken(UserBridge bridge) {
        if (bridge.oauthRefreshTokenCiphertext == null || bridge.oauthRefreshTokenNonce == null) {
            return fallbackMicrosoftRefreshToken(bridge).orElse("");
        }
        return secretEncryptionService.decrypt(
                bridge.oauthRefreshTokenCiphertext,
                bridge.oauthRefreshTokenNonce,
                bridge.keyVersion,
                "user-bridge:" + bridge.userId + ":" + bridge.bridgeId + ":oauth-refresh-token");
    }

    private UserBridgeView toView(
            UserBridge bridge,
            ImportStats importStats,
            dev.inboxbridge.dto.SourcePollingStateView pollingState) {
        PollingSettingsService.EffectivePollingSettings effectiveSettings = sourcePollingSettingsService.effectiveSettingsFor(
                new dev.inboxbridge.domain.RuntimeBridge(
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
        return new UserBridgeView(
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
                pollingState);
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

    private String tokenStorageMode(UserBridge bridge) {
        if (bridge.authMethod == BridgeConfig.AuthMethod.PASSWORD) {
            return "PASSWORD";
        }
        if (bridge.oauthProvider != BridgeConfig.OAuthProvider.MICROSOFT) {
            return "UNKNOWN";
        }
        return hasEffectiveOAuthRefreshToken(bridge) ? "DATABASE" : "NOT_CONFIGURED";
    }

    private boolean hasEffectiveOAuthRefreshToken(UserBridge bridge) {
        return bridge.oauthRefreshTokenCiphertext != null
                || fallbackMicrosoftRefreshToken(bridge).filter(token -> !token.isBlank()).isPresent();
    }

    private Optional<String> fallbackMicrosoftRefreshToken(UserBridge bridge) {
        if (bridge.oauthProvider != BridgeConfig.OAuthProvider.MICROSOFT || !oAuthCredentialService.secureStorageConfigured()) {
            return Optional.empty();
        }
        return oAuthCredentialService.findMicrosoftCredential(bridge.bridgeId)
                .map(OAuthCredentialService.StoredOAuthCredential::refreshToken)
                .filter(token -> token != null && !token.isBlank());
    }

    private AdminPollEventSummary sanitizeLastEvent(UserBridge bridge, AdminPollEventSummary lastEvent) {
        if (lastEvent == null || !"ERROR".equals(lastEvent.status()) || lastEvent.error() == null) {
            return lastEvent;
        }
        if (!lastEvent.error().contains("configured for OAuth2 but has no refresh token")) {
            return lastEvent;
        }
        OAuthCredentialService.StoredOAuthCredential credential = oAuthCredentialService.findMicrosoftCredential(bridge.bridgeId).orElse(null);
        if (credential == null || credential.updatedAt() == null || lastEvent.finishedAt() == null) {
            return lastEvent;
        }
        if (credential.updatedAt().isAfter(lastEvent.finishedAt())) {
            return null;
        }
        return lastEvent;
    }

    private BridgeConfig.Protocol parseProtocol(String value) {
        return value == null || value.isBlank() ? BridgeConfig.Protocol.IMAP : BridgeConfig.Protocol.valueOf(value.toUpperCase());
    }

    private BridgeConfig.AuthMethod parseAuthMethod(String value) {
        return value == null || value.isBlank() ? BridgeConfig.AuthMethod.PASSWORD : BridgeConfig.AuthMethod.valueOf(value.toUpperCase());
    }

    private BridgeConfig.OAuthProvider parseOAuthProvider(String value) {
        return value == null || value.isBlank() ? BridgeConfig.OAuthProvider.NONE : BridgeConfig.OAuthProvider.valueOf(value.toUpperCase());
    }

    private int defaultPort(BridgeConfig.Protocol protocol) {
        return protocol == BridgeConfig.Protocol.IMAP ? 993 : 995;
    }

    private UserBridge resolveExistingBridge(AppUser user, UpdateUserBridgeRequest request) {
        String originalBridgeId = blankToNull(request.originalBridgeId());
        if (originalBridgeId == null) {
            return null;
        }
        return repository.findByBridgeId(originalBridgeId)
                .filter(existing -> existing.userId.equals(user.id))
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
    }

    private String resolvePassword(UserBridge existing, BridgeConfig.AuthMethod authMethod, String password) {
        if (authMethod != BridgeConfig.AuthMethod.PASSWORD) {
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
            UserBridge existing,
            BridgeConfig.AuthMethod authMethod,
            BridgeConfig.OAuthProvider oauthProvider,
            String oauthRefreshToken) {
        if (authMethod != BridgeConfig.AuthMethod.OAUTH2 || oauthProvider == BridgeConfig.OAuthProvider.NONE) {
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
