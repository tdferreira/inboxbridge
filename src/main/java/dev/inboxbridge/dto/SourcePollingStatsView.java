package dev.inboxbridge.dto;

import java.util.List;
import java.util.Map;

/**
 * Polling/import metrics scoped to one mail fetcher only.
 */
public record SourcePollingStatsView(
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
        Map<String, List<ImportTimelinePointView>> idleRunTimelines,
        PollingHealthSummaryView health,
        List<PollingBreakdownItemView> providerBreakdown,
        long manualRuns,
        long scheduledRuns,
        long idleRuns,
        long averagePollDurationMillis) {
}
