package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.persistence.SourcePollEvent;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PollingTimelineServiceTest {

    @Test
    void buildTimelinesFromInstantsProducesExpectedPresetBucketSizes() {
        PollingTimelineService service = new PollingTimelineService();
        Instant importAt = Instant.now().minusSeconds(3600);

        var timelines = service.buildTimelinesFromInstants(List.of(importAt), ZoneOffset.UTC);

        assertEquals(24, timelines.get("today").size());
        assertEquals(24, timelines.get("yesterday").size());
        assertEquals(168, timelines.get("pastWeek").size());
        assertEquals(30, timelines.get("pastMonth").size());
        assertEquals(13, timelines.get("pastTrimester").size());
        assertEquals(26, timelines.get("pastSemester").size());
        assertEquals(52, timelines.get("pastYear").size());
    }

    @Test
    void buildTimelineBundleKeepsHourlyBucketsForRangesUpToOneWeek() {
        PollingTimelineService service = new PollingTimelineService();
        Instant importAt = Instant.parse("2026-03-04T09:15:00Z");

        PollingTimelineBundleView timeline = service.buildTimelineBundle(
                List.of(importAt),
                List.of(),
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-07T23:00:00Z"),
                ZoneOffset.UTC);

        assertEquals(168, timeline.importTimelines().get("custom").size());
    }

    @Test
    void buildTimelineBundleSwitchesToDailyAndMonthlyBucketsForLongerRanges() {
        PollingTimelineService service = new PollingTimelineService();
        Instant eventAt = Instant.parse("2026-03-15T09:15:00Z");
        SourcePollEvent errorEvent = new SourcePollEvent();
        errorEvent.finishedAt = eventAt;
        errorEvent.status = "ERROR";
        errorEvent.triggerName = "scheduler";

        PollingTimelineBundleView dailyTimeline = service.buildTimelineBundle(
                List.of(eventAt),
                List.of(errorEvent),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-04-01T00:00:00Z"),
                ZoneOffset.UTC);
        PollingTimelineBundleView monthlyTimeline = service.buildTimelineBundle(
                List.of(eventAt),
                List.of(errorEvent),
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                ZoneOffset.UTC);

        assertEquals(90, dailyTimeline.importTimelines().get("custom").size());
        assertEquals(90, dailyTimeline.errorTimelines().get("custom").size());
        assertEquals(19, monthlyTimeline.importTimelines().get("custom").size());
        assertEquals(19, monthlyTimeline.scheduledRunTimelines().get("custom").size());
    }
}
