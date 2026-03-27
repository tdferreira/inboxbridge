package dev.inboxbridge.dto;

/**
 * Snapshot of the current mail-fetcher health distribution for a statistics
 * scope.
 */
public record PollingHealthSummaryView(
        int activeMailFetchers,
        int coolingDownMailFetchers,
        int failingMailFetchers,
        int disabledMailFetchers) {
}
