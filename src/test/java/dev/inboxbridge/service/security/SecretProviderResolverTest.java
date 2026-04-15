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
        assertEquals(1, resolver.componentStatuses().size());
        assertEquals("local-key", resolver.componentStatuses().getFirst().componentId());
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
        assertEquals(1, resolver.componentStatuses().size());
        assertEquals("vault_transit-transit", resolver.componentStatuses().getFirst().componentId());
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
        assertEquals(2, resolver.componentStatuses().size());
        assertEquals("split-secondary", resolver.componentStatuses().get(1).componentId());
        assertEquals("SPLIT_KEY:LOCAL=v1|OPENBAO_TRANSIT=inboxbridge", resolver.activeKeyVersion());
        assertEquals("LOCAL:v1 + OPENBAO_TRANSIT:inboxbridge", resolver.activeKeyId());
    }

    @Test
    void reportsSplitKeySecondaryComponentWhenSplitModeIsMisconfigured() {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(configuredLocalProvider());
        resolver.setProviderMode("SPLIT_KEY");

        assertEquals(2, resolver.componentStatuses().size());
        assertEquals("split-secondary", resolver.componentStatuses().get(1).componentId());
        assertFalse(resolver.componentStatuses().get(1).writable());
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

    @Test
    void detectsWhenActiveTransitCiphertextCanBeMetadataRewrapped() {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(configuredLocalProvider());
        resolver.setProviderMode("OPENBAO_TRANSIT");
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true, 3));

        assertTrue(resolver.canMetadataRewrapToActive("vault:v1:payload", "", "OPENBAO_TRANSIT:inboxbridge"));
        assertFalse(resolver.canMetadataRewrapToActive("vault:v3:payload", "", "OPENBAO_TRANSIT:inboxbridge"));
        assertFalse(resolver.canMetadataRewrapToActive("vault:v1:payload", "nonce", "OPENBAO_TRANSIT:inboxbridge"));
    }

    @Test
    void canAssessANonCurrentTransitModeWithoutSwitchingTheActiveMode() {
        SecretProviderResolver resolver = configuredLocalResolver();
        resolver.setOpenbaoUrl("https://openbao.internal");
        resolver.setOpenbaoToken("token");
        resolver.setOpenbaoMount("transit");
        resolver.setOpenbaoKey("inboxbridge");
        resolver.setTransitSecretProvider(new StubTransitSecretProvider(true));

        SecretProviderHealth health = resolver.healthForMode(SecretProviderMode.OPENBAO_TRANSIT);

        assertEquals(SecretProviderMode.OPENBAO_TRANSIT, health.mode());
        assertTrue(health.writable());
        assertEquals("OPENBAO_TRANSIT:inboxbridge", resolver.activeKeyVersionForMode(SecretProviderMode.OPENBAO_TRANSIT));
        assertEquals("inboxbridge", resolver.activeKeyIdForMode(SecretProviderMode.OPENBAO_TRANSIT));
        assertTrue(resolver.isModeCurrent(SecretProviderMode.LOCAL));
        assertFalse(resolver.isModeCurrent(SecretProviderMode.OPENBAO_TRANSIT));
    }

    @Test
    void returnsNullActiveKeyDetailsWhenAssessedModeIsNotWritable() {
        SecretProviderResolver resolver = configuredLocalResolver();

        assertFalse(resolver.healthForMode(SecretProviderMode.VAULT_TRANSIT).writable());
        assertEquals(null, resolver.activeKeyVersionForMode(SecretProviderMode.VAULT_TRANSIT));
        assertEquals(null, resolver.activeKeyIdForMode(SecretProviderMode.VAULT_TRANSIT));
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
        private final int latestVersion;

        private StubTransitSecretProvider(boolean healthy) {
            this(healthy, 1);
        }

        private StubTransitSecretProvider(boolean healthy, int latestVersion) {
            this.healthy = healthy;
            this.latestVersion = latestVersion;
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

        @Override
        public java.util.OptionalInt latestKeyVersion(TransitProviderConfig config) {
            return healthy ? java.util.OptionalInt.of(latestVersion) : java.util.OptionalInt.empty();
        }
    }
}
