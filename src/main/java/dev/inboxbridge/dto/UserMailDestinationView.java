package dev.inboxbridge.dto;

public record UserMailDestinationView(
        String provider,
        String deliveryMode,
        boolean configured,
        boolean linked,
        boolean passwordConfigured,
        boolean oauthConnected,
        boolean sharedGoogleClientConfigured,
        boolean sharedMicrosoftClientConfigured,
        String googleRedirectUri,
        String microsoftRedirectUri,
        String host,
        Integer port,
        boolean tls,
        String authMethod,
        String oauthProvider,
        String username,
        String folder) {
}