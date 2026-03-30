package dev.inboxbridge.dto;

public record RegisterUserRequest(
        String username,
        String password,
        String confirmPassword,
        String challengeId,
        String challengeAnswer) {
}
