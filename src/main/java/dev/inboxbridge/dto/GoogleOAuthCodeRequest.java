package dev.inboxbridge.dto;

public record GoogleOAuthCodeRequest(String code, String state) {
}
