package dev.inboxbridge.dto;

import java.util.Map;

public record ApiError(String code, String message, String details, Map<String, String> meta) {
    public ApiError(String code, String message) {
        this(code, message, null, Map.of());
    }

    public ApiError(String code, String message, String details) {
        this(code, message, details, Map.of());
    }
}
