package dev.inboxbridge.dto;

public record ExtensionAuthLoginRequest(
        String username,
        String password,
        String label,
        String browserFamily,
        String extensionVersion) {
}
