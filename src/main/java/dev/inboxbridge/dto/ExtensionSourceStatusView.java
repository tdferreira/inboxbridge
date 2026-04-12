package dev.inboxbridge.dto;

import java.time.Instant;

public record ExtensionSourceStatusView(
        String sourceId,
        String label,
        boolean enabled,
        String status,
        Instant lastRunAt,
        int lastFetched,
        int lastImported,
        int lastDuplicates,
        String lastError,
        boolean needsAttention) {
}
