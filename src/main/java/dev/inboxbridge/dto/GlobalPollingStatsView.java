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
        long errorPolls,
        List<ImportTimelinePointView> importsByDay,
        Map<String, List<ImportTimelinePointView>> importTimelines,
        Map<String, List<ImportTimelinePointView>> duplicateTimelines,
        Map<String, List<ImportTimelinePointView>> errorTimelines,
        Map<String, List<ImportTimelinePointView>> manualRunTimelines,
        Map<String, List<ImportTimelinePointView>> scheduledRunTimelines,
        PollingHealthSummaryView health,
        List<PollingBreakdownItemView> providerBreakdown,
        long manualRuns,
        long scheduledRuns,
        long averagePollDurationMillis) {
}
