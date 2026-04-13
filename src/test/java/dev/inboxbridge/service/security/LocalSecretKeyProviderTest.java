package dev.inboxbridge.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class LocalSecretKeyProviderTest {

    @Test
    void resolvesActiveAndLegacyKeys() {
        LocalSecretKeyProvider provider = configuredProvider();

        assertEquals("LOCAL", provider.providerId());
        assertTrue(provider.resolveKey("LOCAL:v2").isPresent());
        assertTrue(provider.resolveKey("v1").isPresent());
        assertFalse(provider.resolveKey("VAULT:v1").isPresent());
    }

    @Test
    void rejectsMalformedLegacyKeyConfig() {
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(base64("0123456789abcdef0123456789abcdef"));
        provider.setTokenEncryptionKeyId("v2");
        provider.setTokenEncryptionLegacyKeys("v1");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> provider.resolveKey("v1"));

        assertEquals(
                "SECURITY_TOKEN_ENCRYPTION_LEGACY_KEYS must use keyId:base64Key entries separated by commas",
                error.getMessage());
    }

    private LocalSecretKeyProvider configuredProvider() {
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(base64("fedcba9876543210fedcba9876543210"));
        provider.setTokenEncryptionKeyId("v2");
        provider.setTokenEncryptionLegacyKeys("v1:" + base64("0123456789abcdef0123456789abcdef"));
        return provider;
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes());
    }
}
