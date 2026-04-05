package dev.inboxbridge.dto;

import java.util.List;
import java.util.Map;

/**
 * Timeline-only polling statistics payload used for custom date-range chart
 * queries without reloading the full statistics cards.
 */
public record PollingTimelineBundleView(
        Map<String, List<ImportTimelinePointView>> importTimelines,
        Map<String, List<ImportTimelinePointView>> duplicateTimelines,
        Map<String, List<ImportTimelinePointView>> errorTimelines,
        Map<String, List<ImportTimelinePointView>> manualRunTimelines,
        Map<String, List<ImportTimelinePointView>> scheduledRunTimelines,
        Map<String, List<ImportTimelinePointView>> idleRunTimelines) {
}
