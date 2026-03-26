package dev.inboxbridge.dto;

public record UpdateUserRequest(
        String role,
        Boolean active,
        Boolean approved,
        Boolean mustChangePassword) {
}
