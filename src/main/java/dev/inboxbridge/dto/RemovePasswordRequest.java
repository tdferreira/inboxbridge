package dev.inboxbridge.dto;

/**
 * Carries the current password required to confirm deliberate password
 * removal before an account becomes passkey-only.
 */
public record RemovePasswordRequest(
        String currentPassword) {
}
