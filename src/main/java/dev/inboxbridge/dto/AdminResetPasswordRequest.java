package dev.inboxbridge.dto;

public record AdminResetPasswordRequest(
        String newPassword,
        String confirmNewPassword) {
}
