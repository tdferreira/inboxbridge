package dev.inboxbridge.dto;

public record AdminDestinationSummary(
        boolean gmailClientConfigured,
        String tokenStorageMode,
        boolean createMissingLabels,
        boolean neverMarkSpam,
        boolean processForCalendar) {
}
