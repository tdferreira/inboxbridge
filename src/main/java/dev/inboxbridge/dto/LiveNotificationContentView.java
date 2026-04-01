package dev.inboxbridge.dto;

import java.util.Map;

public record LiveNotificationContentView(
        String kind,
        String key,
        Map<String, String> params) {
}
