package dev.inboxbridge.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class SecretEncryptionServiceTest {

    @Test
    void encryptsAndDecryptsUsingContextBoundAad() {
        SecretEncryptionService service = configuredService();

        SecretEncryptionService.EncryptedValue encrypted = service.encrypt("refresh-token-123", "MICROSOFT:source-1:refresh");
        String decrypted = service.decrypt(
                encrypted.ciphertextBase64(),
                encrypted.nonceBase64(),
                "v1",
                "MICROSOFT:source-1:refresh");

        assertNotEquals("refresh-token-123", encrypted.ciphertextBase64());
        assertEquals("refresh-token-123", decrypted);
    }

    @Test
    void rejectsDecryptingWithDifferentContext() {
        SecretEncryptionService service = configuredService();
        SecretEncryptionService.EncryptedValue encrypted = service.encrypt("refresh-token-123", "MICROSOFT:source-1:refresh");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.decrypt(
                        encrypted.ciphertextBase64(),
                        encrypted.nonceBase64(),
                        "v1",
                        "MICROSOFT:source-2:refresh"));

        assertTrue(error.getMessage().contains("Secret decryption failed"));
    }

    @Test
    void reportsUnconfiguredWhenKeyIsMissing() {
        SecretEncryptionService service = new SecretEncryptionService();
        service.tokenEncryptionKey = "replace-me";
        service.tokenEncryptionKeyId = "v1";

        assertFalse(service.isConfigured());
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.keyVersion());
        assertEquals("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.", error.getMessage());
    }

    private SecretEncryptionService configuredService() {
        SecretEncryptionService service = new SecretEncryptionService();
        service.tokenEncryptionKey = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        service.tokenEncryptionKeyId = "v1";
        return service;
    }
}
