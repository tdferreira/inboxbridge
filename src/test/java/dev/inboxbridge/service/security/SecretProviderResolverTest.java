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
    void reportsHealthyWritableTransitProviderWhenConfiguredAndReachable() {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setProviderMode("VAULT_TRANSIT");
        resolver.setVaultUrl("https://vault.internal");
        resolver.setVaultToken("token");
        resolver.setVaultMount("transit");
        resolver.setVaultKey("inboxbridge");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true));

        SecretProviderHealth health = resolver.health();

        assertEquals(SecretProviderMode.VAULT_TRANSIT, health.mode());
        assertTrue(health.healthy());
        assertTrue(health.writable());
        assertEquals("VAULT_TRANSIT", health.providerId());
        assertEquals("VAULT_TRANSIT:inboxbridge", resolver.activeKeyVersion());
        assertEquals("inboxbridge", resolver.activeKeyId());
    }

    @Test
    void reportsHealthyWritableSplitKeyProviderWhenConfiguredAndReachable() {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(configuredLocalProvider());
        resolver.setProviderMode("SPLIT_KEY");
        resolver.setSplitSecondaryMode("OPENBAO_TRANSIT");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true));

        SecretProviderHealth health = resolver.health();

        assertEquals(SecretProviderMode.SPLIT_KEY, health.mode());
        assertTrue(health.healthy());
        assertTrue(health.writable());
        assertEquals("SPLIT_KEY:LOCAL=v1|OPENBAO_TRANSIT=inboxbridge", resolver.activeKeyVersion());
        assertEquals("LOCAL:v1 + OPENBAO_TRANSIT:inboxbridge", resolver.activeKeyId());
    }

    @Test
    void marksTransitStoredKeyVersionAvailableWhenProviderIsHealthy() {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setProviderMode("OPENBAO_TRANSIT");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("active-key");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true));

        assertTrue(resolver.isStoredKeyVersionAvailable("OPENBAO_TRANSIT:legacy-key"));
    }

    @Test
    void marksTransitStoredKeyVersionUnavailableWhenProviderHealthFails() {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setProviderMode("OPENBAO_TRANSIT");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("active-key");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(false));

        assertFalse(resolver.isStoredKeyVersionAvailable("OPENBAO_TRANSIT:legacy-key"));
    }

    @Test
    void splitKeyStoredKeyVersionRequiresBothLocalAndTransitAvailability() {
        LocalSecretKeyProvider provider = configuredLocalProvider();
        provider.setTokenEncryptionLegacyKeys("");
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(provider);
        resolver.setProviderMode("SPLIT_KEY");
        resolver.setSplitSecondaryMode("OPENBAO_TRANSIT");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true));

        assertFalse(resolver.isStoredKeyVersionAvailable("SPLIT_KEY:LOCAL=legacy|OPENBAO_TRANSIT=inboxbridge"));
    }

    private SecretProviderResolver configuredLocalResolver() {
        LocalSecretKeyProvider provider = configuredLocalProvider();
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(provider);
        resolver.setProviderMode("LOCAL");
        return resolver;
    }

    private LocalSecretKeyProvider configuredLocalProvider() {
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
        provider.setTokenEncryptionKeyId("v1");
        return provider;
    }

    private static final class StubTransitSecretProvider extends TransitSecretProvider {
        private final boolean healthy;

        private StubTransitSecretProvider(boolean healthy) {
            this.healthy = healthy;
        }

        @Override
        public SecretProviderHealth health(TransitProviderConfig config) {
            return new SecretProviderHealth(
                    config.mode(),
                    config.providerId(),
                    healthy,
                    healthy,
                    healthy ? config.mode().name() + " transit provider is ready." : "Transit provider unavailable");
        }
    }
}
