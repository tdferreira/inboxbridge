package dev.inboxbridge.service.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.SecurityTokenConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves the configured deployment-level secret-provider mode and reports
 * whether it is healthy enough for new encrypted writes.
 */
@ApplicationScoped
public class SecretProviderResolver {

    @Inject
    SecurityTokenConfig securityTokenConfig;

    @Inject
    LocalSecretKeyProvider localSecretKeyProvider;

    String providerMode;
    String openbaoUrl;
    String openbaoToken;
    String openbaoMount;
    String openbaoKey;
    String vaultUrl;
    String vaultToken;
    String vaultMount;
    String vaultKey;

    public void setProviderMode(String providerMode) {
        this.providerMode = providerMode;
    }

    public void setOpenbaoUrl(String openbaoUrl) {
        this.openbaoUrl = openbaoUrl;
    }

    public void setOpenbaoToken(String openbaoToken) {
        this.openbaoToken = openbaoToken;
    }

    public void setOpenbaoMount(String openbaoMount) {
        this.openbaoMount = openbaoMount;
    }

    public void setOpenbaoKey(String openbaoKey) {
        this.openbaoKey = openbaoKey;
    }

    public void setVaultUrl(String vaultUrl) {
        this.vaultUrl = vaultUrl;
    }

    public void setVaultToken(String vaultToken) {
        this.vaultToken = vaultToken;
    }

    public void setVaultMount(String vaultMount) {
        this.vaultMount = vaultMount;
    }

    public void setVaultKey(String vaultKey) {
        this.vaultKey = vaultKey;
    }

    public void setLocalSecretKeyProvider(LocalSecretKeyProvider localSecretKeyProvider) {
        this.localSecretKeyProvider = localSecretKeyProvider;
    }

    public SecretProviderMode mode() {
        return SecretProviderMode.parse(configuredProviderMode());
    }

    public SecretProviderHealth health() {
        return switch (mode()) {
            case LOCAL -> localHealth();
            case OPENBAO_TRANSIT -> unsupportedTransitHealth(
                    SecretProviderMode.OPENBAO_TRANSIT,
                    configuredOpenbaoUrl(),
                    configuredOpenbaoToken(),
                    configuredOpenbaoMount(),
                    configuredOpenbaoKey(),
                    "SECRET_PROVIDER_OPENBAO_URL",
                    "SECRET_PROVIDER_OPENBAO_TOKEN",
                    "SECRET_PROVIDER_OPENBAO_MOUNT",
                    "SECRET_PROVIDER_OPENBAO_KEY");
            case VAULT_TRANSIT -> unsupportedTransitHealth(
                    SecretProviderMode.VAULT_TRANSIT,
                    configuredVaultUrl(),
                    configuredVaultToken(),
                    configuredVaultMount(),
                    configuredVaultKey(),
                    "SECRET_PROVIDER_VAULT_URL",
                    "SECRET_PROVIDER_VAULT_TOKEN",
                    "SECRET_PROVIDER_VAULT_MOUNT",
                    "SECRET_PROVIDER_VAULT_KEY");
            case SPLIT_KEY -> new SecretProviderHealth(
                    SecretProviderMode.SPLIT_KEY,
                    SecretProviderMode.SPLIT_KEY.name(),
                    false,
                    false,
                    "Secret provider SPLIT_KEY is not implemented yet.");
        };
    }

    public boolean isWritable() {
        return health().writable();
    }

    public String activeProviderId() {
        return health().providerId();
    }

    public SecretKeyProvider requireWritableProvider() {
        SecretProviderHealth health = health();
        if (!health.writable()) {
            throw new IllegalStateException(health.statusMessage());
        }
        return switch (health.mode()) {
            case LOCAL -> localProvider();
            default -> throw new IllegalStateException(health.statusMessage());
        };
    }

    public Optional<SecretKeyMaterial> resolveKey(String storedKeyVersion) {
        StoredSecretKeyReference reference = StoredSecretKeyReference.parse(storedKeyVersion);
        if (LocalSecretKeyProvider.PROVIDER_ID.equals(reference.providerId())) {
            return localProvider().resolveKey(storedKeyVersion);
        }
        return Optional.empty();
    }

    private SecretProviderHealth localHealth() {
        if (!localProvider().isConfigured()) {
            return new SecretProviderHealth(
                    SecretProviderMode.LOCAL,
                    localProvider().providerId(),
                    false,
                    false,
                    "Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.");
        }
        return new SecretProviderHealth(
                SecretProviderMode.LOCAL,
                localProvider().providerId(),
                true,
                true,
                "Local secret provider is ready.");
    }

    private SecretProviderHealth unsupportedTransitHealth(
            SecretProviderMode mode,
            String url,
            String token,
            String mount,
            String key,
            String urlEnv,
            String tokenEnv,
            String mountEnv,
            String keyEnv) {
        List<String> missing = new ArrayList<>();
        if (isBlank(url)) {
            missing.add(urlEnv);
        }
        if (isBlank(token)) {
            missing.add(tokenEnv);
        }
        if (isBlank(mount)) {
            missing.add(mountEnv);
        }
        if (isBlank(key)) {
            missing.add(keyEnv);
        }
        if (!missing.isEmpty()) {
            return new SecretProviderHealth(
                    mode,
                    mode.name(),
                    false,
                    false,
                    "Secret provider " + mode.name() + " is selected but the following settings are missing: " + String.join(", ", missing) + ".");
        }
        return new SecretProviderHealth(
                mode,
                mode.name(),
                false,
                false,
                "Secret provider " + mode.name() + " is configured, but transit-backed secret encryption is not implemented yet.");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String configuredProviderMode() {
        if (providerMode != null) {
            return providerMode;
        }
        if (securityTokenConfig == null) {
            return SecretProviderMode.LOCAL.name();
        }
        return securityTokenConfig.providerMode();
    }

    private String configuredOpenbaoUrl() {
        if (openbaoUrl != null) {
            return openbaoUrl;
        }
        if (securityTokenConfig == null) {
            return null;
        }
        return securityTokenConfig.openbaoUrl().orElse(null);
    }

    private String configuredOpenbaoToken() {
        if (openbaoToken != null) {
            return openbaoToken;
        }
        if (securityTokenConfig == null) {
            return null;
        }
        return securityTokenConfig.openbaoToken().orElse(null);
    }

    private String configuredOpenbaoMount() {
        if (openbaoMount != null) {
            return openbaoMount;
        }
        if (securityTokenConfig == null) {
            return null;
        }
        return securityTokenConfig.openbaoMount().orElse(null);
    }

    private String configuredOpenbaoKey() {
        if (openbaoKey != null) {
            return openbaoKey;
        }
        if (securityTokenConfig == null) {
            return null;
        }
        return securityTokenConfig.openbaoKey().orElse(null);
    }

    private String configuredVaultUrl() {
        if (vaultUrl != null) {
            return vaultUrl;
        }
        if (securityTokenConfig == null) {
            return null;
        }
        return securityTokenConfig.vaultUrl().orElse(null);
    }

    private String configuredVaultToken() {
        if (vaultToken != null) {
            return vaultToken;
        }
        if (securityTokenConfig == null) {
            return null;
        }
        return securityTokenConfig.vaultToken().orElse(null);
    }

    private String configuredVaultMount() {
        if (vaultMount != null) {
            return vaultMount;
        }
        if (securityTokenConfig == null) {
            return null;
        }
        return securityTokenConfig.vaultMount().orElse(null);
    }

    private String configuredVaultKey() {
        if (vaultKey != null) {
            return vaultKey;
        }
        if (securityTokenConfig == null) {
            return null;
        }
        return securityTokenConfig.vaultKey().orElse(null);
    }

    private LocalSecretKeyProvider localProvider() {
        if (localSecretKeyProvider == null) {
            localSecretKeyProvider = new LocalSecretKeyProvider();
        }
        return localSecretKeyProvider;
    }
}
