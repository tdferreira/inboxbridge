package dev.inboxbridge.dto;

import java.time.Instant;

public record EncryptedConfigBackupView(
        Instant generatedAt,
        String algorithm,
        String publicKeyFingerprint,
        String encryptedKey,
        String nonce,
        String ciphertext,
        String warning) {
}
