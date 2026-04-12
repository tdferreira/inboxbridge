package dev.inboxbridge.dto;

public record ExtensionAuthPasskeyVerifyRequest(
        String ceremonyId,
        String credentialJson,
        String label,
        String browserFamily,
        String extensionVersion) {
}
