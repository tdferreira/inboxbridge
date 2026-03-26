package dev.inboxbridge.dto;

public record AdminOverallSummary(
        int configuredSources,
        int enabledSources,
        long totalImportedMessages,
        int sourcesWithErrors,
        boolean pollEnabled,
        String pollInterval,
        int fetchWindow) {
}
