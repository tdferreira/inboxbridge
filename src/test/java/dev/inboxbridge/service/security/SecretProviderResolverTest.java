package dev.inboxbridge.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class SecretProviderResolverTest {

    @Test
    void reportsHealthyWritableLocalProviderWhenKeyIsConfigured() {
        SecretProviderResolver resolver = configuredLocalResolver();

        SecretProviderHealth health = resolver.health();

        assertEquals(SecretProviderMode.LOCAL, health.mode());
        assertEquals("LOCAL", health.providerId());
        assertTrue(health.healthy());
        assertTrue(health.writable());
        assertEquals("Local secret provider is ready.", health.statusMessage());
    }

    @Test
    void reportsMissingTransitSettingsBeforeTransitSupportExists() {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setProviderMode("OPENBAO_TRANSIT");

        SecretProviderHealth health = resolver.health();

        assertEquals(SecretProviderMode.OPENBAO_TRANSIT, health.mode());
        assertFalse(health.healthy());
        assertFalse(health.writable());
        assertTrue(health.statusMessage().contains("SECRET_PROVIDER_OPENBAO_URL"));
    }

    @Test
    void reportsUnsupportedTransitImplementationEvenWhenConfigured() {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setProviderMode("VAULT_TRANSIT");
        resolver.setVaultUrl("https://vault.internal");
        resolver.setVaultToken("token");
        resolver.setVaultMount("transit");
        resolver.setVaultKey("inboxbridge");

        SecretProviderHealth health = resolver.health();

        assertEquals(SecretProviderMode.VAULT_TRANSIT, health.mode());
        assertFalse(health.healthy());
        assertFalse(health.writable());
        assertEquals(
                "Secret provider VAULT_TRANSIT is configured, but transit-backed secret encryption is not implemented yet.",
                health.statusMessage());
    }

    @Test
    void requireWritableProviderFailsClosedForUnsupportedModes() {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setProviderMode("SPLIT_KEY");

        IllegalStateException error = assertThrows(IllegalStateException.class, resolver::requireWritableProvider);

        assertEquals("Secret provider SPLIT_KEY is not implemented yet.", error.getMessage());
    }

    private SecretProviderResolver configuredLocalResolver() {
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        provider.setTokenEncryptionKeyId("v1");
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(provider);
        resolver.setProviderMode("LOCAL");
        return resolver;
    }
}
