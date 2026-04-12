package dev.inboxbridge.dto;

public record ExtensionUserView(
        String username,
        String displayName,
        String language,
        String themeMode) {
}
