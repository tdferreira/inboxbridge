package dev.inboxbridge.dto;

/**
 * Small labeled count entry used by the polling statistics panels for provider
 * and trigger breakdowns.
 */
public record PollingBreakdownItemView(
        String key,
        String label,
        long count) {
}
