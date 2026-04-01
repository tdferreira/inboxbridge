package dev.inboxbridge.dto;

/**
 * Describes one saved admin-ui notification entry.
 */
public record UserUiNotificationView(
        String id,
        Object message,
        Object copyText,
        String tone,
        String targetId,
        String groupKey,
        Long createdAt,
        Boolean floatingVisible,
        Long autoCloseMs) {
}
