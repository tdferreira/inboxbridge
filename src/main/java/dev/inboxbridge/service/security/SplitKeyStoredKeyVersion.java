package dev.inboxbridge.service.security;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes a split-key record where a local key protects the inner payload and
 * a transit provider protects the outer envelope.
 */
public record SplitKeyStoredKeyVersion(String localKeyId, SecretProviderMode transitMode, String transitKeyId) {

    public static SplitKeyStoredKeyVersion parse(String storedKeyVersion) {
        StoredSecretKeyReference reference = StoredSecretKeyReference.parse(storedKeyVersion);
        if (!SecretProviderMode.SPLIT_KEY.name().equals(reference.providerId())) {
            throw new IllegalArgumentException("Stored secret key version is not a split-key reference");
        }
        Map<String, String> segments = new LinkedHashMap<>();
        for (String entry : reference.keyId().split("\\|")) {
            String normalized = entry == null ? "" : entry.trim();
            if (normalized.isBlank()) {
                continue;
            }
            int separator = normalized.indexOf('=');
            if (separator <= 0 || separator == normalized.length() - 1) {
                throw new IllegalArgumentException("Split-key stored key version must use PROVIDER=keyId segments");
            }
            segments.put(
                    normalized.substring(0, separator).trim(),
                    normalized.substring(separator + 1).trim());
        }
        String localKeyId = segments.remove(LocalSecretKeyProvider.PROVIDER_ID);
        if (localKeyId == null || localKeyId.isBlank()) {
            throw new IllegalArgumentException("Split-key stored key version must include LOCAL=<keyId>");
        }
        if (segments.size() != 1) {
            throw new IllegalArgumentException("Split-key stored key version must include exactly one transit provider segment");
        }
        Map.Entry<String, String> transitEntry = segments.entrySet().iterator().next();
        SecretProviderMode transitMode = SecretProviderMode.parse(transitEntry.getKey());
        if (transitMode != SecretProviderMode.OPENBAO_TRANSIT && transitMode != SecretProviderMode.VAULT_TRANSIT) {
            throw new IllegalArgumentException("Split-key stored key version only supports OPENBAO_TRANSIT or VAULT_TRANSIT");
        }
        return new SplitKeyStoredKeyVersion(localKeyId, transitMode, transitEntry.getValue());
    }

    public String storedKeyVersion() {
        return SecretProviderMode.SPLIT_KEY.name()
                + ":"
                + LocalSecretKeyProvider.PROVIDER_ID
                + "="
                + localKeyId
                + "|"
                + transitMode.name()
                + "="
                + transitKeyId;
    }

    public String localStoredKeyVersion() {
        return LocalSecretKeyProvider.PROVIDER_ID + ":" + localKeyId;
    }

    public String summary() {
        return localStoredKeyVersion() + " + " + transitMode.name() + ":" + transitKeyId;
    }
}
