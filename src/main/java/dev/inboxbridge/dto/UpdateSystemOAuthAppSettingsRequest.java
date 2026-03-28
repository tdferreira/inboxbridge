package dev.inboxbridge.dto;

public record UpdateSystemOAuthAppSettingsRequest(
        Boolean multiUserEnabledOverride,
        String googleDestinationUser,
        String googleRedirectUri,
        String googleClientId,
        String googleClientSecret,
        String googleRefreshToken,
        String microsoftClientId,
        String microsoftClientSecret) {
}
