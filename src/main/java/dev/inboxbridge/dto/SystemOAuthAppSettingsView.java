package dev.inboxbridge.dto;

public record SystemOAuthAppSettingsView(
        boolean effectiveMultiUserEnabled,
        Boolean multiUserEnabledOverride,
        String googleDestinationUser,
        String googleRedirectUri,
        String googleClientId,
        boolean googleClientSecretConfigured,
        boolean googleRefreshTokenConfigured,
        String microsoftClientId,
        String microsoftRedirectUri,
        boolean microsoftClientSecretConfigured,
        boolean secureStorageConfigured) {
}
