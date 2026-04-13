package dev.inboxbridge.service.security;

/**
 * Enumerates the deployment-level secret-provider modes planned by the
 * stronger secret-management roadmap.
 */
public enum SecretProviderMode {
    LOCAL,
    OPENBAO_TRANSIT,
    VAULT_TRANSIT,
    SPLIT_KEY;

    public static SecretProviderMode parse(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }
        return SecretProviderMode.valueOf(value.trim().toUpperCase());
    }
}
