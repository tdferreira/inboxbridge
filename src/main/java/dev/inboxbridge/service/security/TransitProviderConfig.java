package dev.inboxbridge.service.security;

/**
 * Normalized configuration for a Vault/OpenBao-compatible transit provider.
 */
public record TransitProviderConfig(
        SecretProviderMode mode,
        String providerId,
        String baseUrl,
        String token,
        String mount,
        String keyName) {

    public String storedKeyVersion() {
        return providerId + ":" + keyName;
    }
}
