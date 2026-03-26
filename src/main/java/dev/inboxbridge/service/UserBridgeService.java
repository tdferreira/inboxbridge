package dev.inboxbridge.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.UpdateUserBridgeRequest;
import dev.inboxbridge.dto.UserBridgeView;
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

    public List<UserBridgeView> listForUser(Long userId) {
        Map<String, ImportStats> importStatsBySource = importStatsBySource();
        return repository.listByUserId(userId).stream()
                .map(bridge -> toView(bridge, importStatsBySource.getOrDefault(bridge.bridgeId, ImportStats.EMPTY)))
                .toList();
    }

    public List<UserBridge> listEnabledBridges() {
        return repository.list("enabled", true);
    }

    public Optional<UserBridge> findByBridgeId(String bridgeId) {
        return repository.findByBridgeId(bridgeId);
    }

    @Transactional
    public UserBridgeView upsert(AppUser user, UpdateUserBridgeRequest request) {
        if (!secretEncryptionService.isConfigured()) {
            throw new IllegalStateException("Secure secret storage must be configured before storing user bridge secrets in the database.");
        }
        String bridgeId = requireNonBlank(request.bridgeId(), "Bridge ID");
        UserBridge bridge = repository.findByBridgeId(bridgeId)
                .filter(existing -> existing.userId.equals(user.id))
                .orElseGet(UserBridge::new);
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
        return toView(bridge, ImportStats.EMPTY);
    }

    @Transactional
    public void delete(AppUser user, String bridgeId) {
        UserBridge bridge = repository.findByBridgeId(bridgeId)
                .filter(existing -> existing.userId.equals(user.id))
                .orElseThrow(() -> new IllegalArgumentException("Unknown bridge id"));
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
            return "";
        }
        return secretEncryptionService.decrypt(
                bridge.oauthRefreshTokenCiphertext,
                bridge.oauthRefreshTokenNonce,
                bridge.keyVersion,
                "user-bridge:" + bridge.userId + ":" + bridge.bridgeId + ":oauth-refresh-token");
    }

    private UserBridgeView toView(UserBridge bridge, ImportStats importStats) {
        AdminPollEventSummary lastEvent = sourcePollEventService.latestForSource(bridge.bridgeId).orElse(null);
        return new UserBridgeView(
                bridge.bridgeId,
                bridge.enabled,
                bridge.protocol.name(),
                bridge.host,
                bridge.port,
                bridge.tls,
                bridge.authMethod.name(),
                bridge.oauthProvider.name(),
                bridge.username,
                bridge.passwordCiphertext != null,
                bridge.oauthRefreshTokenCiphertext != null,
                bridge.folderName == null ? "INBOX" : bridge.folderName,
                bridge.unreadOnly,
                bridge.customLabel == null ? "" : bridge.customLabel,
                tokenStorageMode(bridge),
                importStats.totalImported(),
                importStats.lastImportedAt(),
                lastEvent);
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
        return bridge.oauthRefreshTokenCiphertext != null ? "DATABASE" : "NOT_CONFIGURED";
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
