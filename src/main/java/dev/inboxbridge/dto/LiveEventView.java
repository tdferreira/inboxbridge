package dev.inboxbridge.dto;

import java.time.Instant;

public record LiveEventView(
        String type,
        Instant timestamp,
        PollLiveView poll,
        LiveNotificationView notification) {
}
