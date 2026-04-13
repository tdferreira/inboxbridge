package dev.inboxbridge.service.security;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.inboxbridge.config.SecurityTokenConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Local symmetric-key provider backed by deployment configuration. It supports
 * one active key plus optional legacy keys for non-breaking rotation.
 */
@ApplicationScoped
public class LocalSecretKeyProvider implements SecretKeyProvider {

    public static final String PROVIDER_ID = "LOCAL";

    @Inject
    SecurityTokenConfig securityTokenConfig;

    String tokenEncryptionKey;

    String tokenEncryptionKeyId;

    String tokenEncryptionLegacyKeys;

    public void setTokenEncryptionKey(String tokenEncryptionKey) {
        this.tokenEncryptionKey = tokenEncryptionKey;
    }

    public void setTokenEncryptionKeyId(String tokenEncryptionKeyId) {
        this.tokenEncryptionKeyId = tokenEncryptionKeyId;
    }

    public void setTokenEncryptionLegacyKeys(String tokenEncryptionLegacyKeys) {
        this.tokenEncryptionLegacyKeys = tokenEncryptionLegacyKeys;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isConfigured() {
        String configuredKey = configuredTokenEncryptionKey();
        return configuredKey != null && !configuredKey.isBlank() && !"replace-me".equals(configuredKey);
    }

    @Override
    public SecretKeyMaterial activeKey() {
        requireConfigured();
        return material(configuredTokenEncryptionKeyId(), configuredTokenEncryptionKey());
    }

    @Override
    public Optional<SecretKeyMaterial> resolveKey(String storedKeyVersion) {
        StoredSecretKeyReference reference = StoredSecretKeyReference.parse(storedKeyVersion);
        if (!PROVIDER_ID.equals(reference.providerId())) {
            return Optional.empty();
        }
        SecretKeyMaterial activeKey = activeKey();
        if (activeKey.keyId().equals(reference.keyId())) {
            return Optional.of(activeKey);
        }
        return Optional.ofNullable(legacyKeyMap().get(reference.keyId()))
                .map(encodedKey -> material(reference.keyId(), encodedKey));
    }

    public Set<String> configuredLegacyKeyIds() {
        return Set.copyOf(legacyKeyMap().keySet());
    }

    private SecretKeyMaterial material(String keyId, String encodedKey) {
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("SECURITY_TOKEN_ENCRYPTION_KEY values must be base64-encoded 32-byte keys");
        }
        return new SecretKeyMaterial(PROVIDER_ID, keyId, keyBytes);
    }

    private Map<String, String> legacyKeyMap() {
        Map<String, String> keys = new LinkedHashMap<>();
        String configuredLegacyKeys = configuredTokenEncryptionLegacyKeys();
        if (configuredLegacyKeys == null || configuredLegacyKeys.isBlank()) {
            return keys;
        }
        for (String entry : configuredLegacyKeys.split(",")) {
            String normalized = entry.trim();
            if (normalized.isBlank()) {
                continue;
            }
            int separator = normalized.indexOf(':');
            if (separator <= 0 || separator == normalized.length() - 1) {
                throw new IllegalStateException(
                        "SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS must use keyId:base64Key entries separated by commas");
            }
            String keyId = normalized.substring(0, separator).trim();
            String encodedKey = normalized.substring(separator + 1).trim();
            if (keyId.isBlank() || encodedKey.isBlank()) {
                throw new IllegalStateException(
                        "SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS must use non-empty keyId:base64Key entries");
            }
            if (keys.putIfAbsent(keyId, encodedKey) != null) {
                throw new IllegalStateException("Duplicate legacy secret key id: " + keyId);
            }
        }
        return keys;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.");
        }
    }

    private String configuredTokenEncryptionKey() {
        if (tokenEncryptionKey != null) {
            return tokenEncryptionKey;
        }
        return securityTokenConfig.tokenEncryptionKey();
    }

    private String configuredTokenEncryptionKeyId() {
        if (tokenEncryptionKeyId != null) {
            return tokenEncryptionKeyId;
        }
        return securityTokenConfig.tokenEncryptionKeyId();
    }

    private String configuredTokenEncryptionLegacyKeys() {
        if (tokenEncryptionLegacyKeys != null) {
            return tokenEncryptionLegacyKeys;
        }
        return securityTokenConfig.tokenEncryptionLegacyKeys().orElse(null);
    }
}
