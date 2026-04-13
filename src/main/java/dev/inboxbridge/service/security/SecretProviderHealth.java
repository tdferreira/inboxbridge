package dev.inboxbridge.service.security;

/**
 * Summarizes whether the currently selected secret-provider mode is both
 * healthy and writable for new encrypted secrets.
 */
public record SecretProviderHealth(
        SecretProviderMode mode,
        String providerId,
        boolean healthy,
        boolean writable,
        String statusMessage) {
}
