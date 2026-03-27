package dev.inboxbridge.dto;

import java.util.List;
import java.util.Map;

/**
 * Admin-only aggregate polling/import metrics across the whole deployment.
 */
public record GlobalPollingStatsView(
        long totalImportedMessages,
        int configuredMailFetchers,
        int enabledMailFetchers,
        int sourcesWithErrors,
        List<ImportTimelinePointView> importsByDay,
        Map<String, List<ImportTimelinePointView>> importTimelines) {
}
