package dev.inboxbridge.dto;

public record ExtensionLastRunSummaryView(
        int fetched,
        int imported,
        int duplicates,
        int errors) {
}
