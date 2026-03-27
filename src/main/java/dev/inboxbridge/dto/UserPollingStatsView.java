package dev.inboxbridge.dto;

import java.util.List;
import java.util.Map;

/**
 * Polling/import metrics scoped to one authenticated user only.
 */
public record UserPollingStatsView(
        long totalImportedMessages,
        int configuredMailFetchers,
        int enabledMailFetchers,
        int sourcesWithErrors,
        List<ImportTimelinePointView> importsByDay,
        Map<String, List<ImportTimelinePointView>> importTimelines) {
}
