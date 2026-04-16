package dev.inboxbridge.dto;

/**
 * Captures the exact secret-management target a queued or completed
 * re-encryption request was created against, so operators can detect whether
 * the active mode/provider/key drifted before execution.
 */
public record SecretReencryptionTargetView(
        String mode,
        String providerId,
        String activeKeyVersion,
        String activeKeyId) {
}
