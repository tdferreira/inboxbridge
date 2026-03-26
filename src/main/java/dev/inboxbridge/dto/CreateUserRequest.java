package dev.inboxbridge.dto;

public record CreateUserRequest(
        String username,
        String password,
        String role) {
}
