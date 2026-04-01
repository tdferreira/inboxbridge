package dev.inboxbridge.dto;

import java.util.List;

public record LiveNotificationView(
        Integer autoCloseMs,
        LiveNotificationContentView copyText,
        String groupKey,
        LiveNotificationContentView message,
        boolean replaceGroup,
        List<String> supersedesGroupKeys,
        String targetId,
        String tone) {
}
