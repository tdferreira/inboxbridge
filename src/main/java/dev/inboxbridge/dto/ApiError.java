package dev.inboxbridge.dto;

public record ApiError(String code, String message, String details) {
    public ApiError(String code, String message) {
        this(code, message, null);
    }
}
