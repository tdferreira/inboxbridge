package dev.inboxbridge.dto;

public record UpdateUserMailDestinationRequest(
        String provider,
        String host,
        Integer port,
        Boolean tls,
        String authMethod,
        String oauthProvider,
        String username,
        String password,
        String folder,
        String spamJunkFolder) {

    public UpdateUserMailDestinationRequest(
            String provider,
            String host,
            Integer port,
            Boolean tls,
            String authMethod,
            String oauthProvider,
            String username,
            String password,
            String folder) {
        this(provider, host, port, tls, authMethod, oauthProvider, username, password, folder, "");
    }
}
