package dev.inboxbridge.dto;

public record RegisterUserRequest(
        String username,
        String password) {
}
