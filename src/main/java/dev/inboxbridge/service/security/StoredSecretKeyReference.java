package dev.inboxbridge.service.security;

/**
 * Parses provider-aware stored key metadata. Older local-only records can keep
 * their unqualified legacy key id so rotations remain backward compatible.
 */
public record StoredSecretKeyReference(String providerId, String keyId, boolean providerQualified) {

    public static StoredSecretKeyReference parse(String storedKeyVersion) {
        if (storedKeyVersion == null || storedKeyVersion.isBlank()) {
            throw new IllegalArgumentException("Stored secret key version is required");
        }
        int separator = storedKeyVersion.indexOf(':');
        if (separator <= 0 || separator == storedKeyVersion.length() - 1) {
            return new StoredSecretKeyReference(LocalSecretKeyProvider.PROVIDER_ID, storedKeyVersion.trim(), false);
        }
        return new StoredSecretKeyReference(
                storedKeyVersion.substring(0, separator).trim(),
                storedKeyVersion.substring(separator + 1).trim(),
                true);
    }
}
