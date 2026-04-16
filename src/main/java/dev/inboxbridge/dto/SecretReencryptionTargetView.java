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

    public String summary() {
        if (activeKeyId != null && !activeKeyId.isBlank()) {
            return activeKeyId;
        }
        if (activeKeyVersion != null && !activeKeyVersion.isBlank()) {
            return activeKeyVersion;
        }
        if (providerId != null && !providerId.isBlank()) {
            return providerId;
        }
        return mode == null ? "" : mode;
    }
}
