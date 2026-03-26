package dev.inboxbridge.dto;

public record MicrosoftOAuthCodeRequest(String sourceId, String code, String state) {
}
