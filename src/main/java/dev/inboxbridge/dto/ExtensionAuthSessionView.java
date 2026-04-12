package dev.inboxbridge.dto;

public record ExtensionAuthSessionView(
        Long id,
        String label,
        String browserFamily,
        String extensionVersion,
        String publicBaseUrl,
        ExtensionUserView user,
        ExtensionAuthTokensView tokens) {
}
