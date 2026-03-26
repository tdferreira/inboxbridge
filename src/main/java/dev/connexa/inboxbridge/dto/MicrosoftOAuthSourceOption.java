package dev.connexa.inboxbridge.dto;

public record MicrosoftOAuthSourceOption(
        String id,
        String protocol,
        boolean enabled) {
}
