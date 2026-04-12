package dev.inboxbridge.dto;

public record ExtensionSessionCreateRequest(
        String label,
        String browserFamily,
        String extensionVersion) {
}
