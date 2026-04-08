package dev.inboxbridge.service.polling;

import dev.inboxbridge.dto.ImportTimelinePointView;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.persistence.SourcePollEvent;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns polling/import timeline bucket generation so stats-oriented services can
 * assemble repository data without each carrying the full time-bucketing
 * implementation inline.
 */
@ApplicationScoped
public class PollingTimelineService {

    static final int HOURLY_BUCKETS = 24;
    static final int PAST_WEEK_HOURS = 7 * 24;
    static final int TRIMESTER_WEEKS = 13;
    static final int SEMESTER_WEEKS = 26;
    static final int YEAR_WEEKS = 52;

    public Map<String, List<ImportTimelinePointView>> buildTimelinesFromInstants(List<Instant> importedAtValues, ZoneId zoneId) {
        return buildTimelinesFromTimedCounts(toTimedCounts(importedAtValues), zoneId);
    }

    public Map<String, List<ImportTimelinePointView>> buildTimelinesFromTimedCounts(List<TimedCount> values, ZoneId zoneId) {
        Map<String, List<ImportTimelinePointView>> timelines = new LinkedHashMap<>();
        timelines.put("today", buildTodayTimeline(values, zoneId));
        timelines.put("yesterday", buildYesterdayTimeline(values, zoneId));
        timelines.put("pastWeek", buildPastWeekTimeline(values, zoneId));
        timelines.put("pastMonth", buildPastMonthTimeline(values, zoneId));
        timelines.put("pastTrimester", buildPastTrimesterTimeline(values, zoneId));
        timelines.put("pastSemester", buildPastSemesterTimeline(values, zoneId));
        timelines.put("pastYear", buildPastYearTimeline(values, zoneId));
        return timelines;
    }

    public List<TimedCount> toTimedCounts(List<Instant> instants) {
        return instants.stream()
                .map(value -> new TimedCount(value, 1L))
                .toList();
    }

    public PollingTimelineBundleView buildTimelineBundle(
            List<Instant> imports,
            List<SourcePollEvent> events,
            Instant fromInclusive,
            Instant toExclusive,
            ZoneId zoneId) {
        return new PollingTimelineBundleView(
                Map.of("custom", buildCustomTimeline(
                        toTimedCounts(imports),
                        fromInclusive,
                        toExclusive,
                        zoneId)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(event -> event.duplicateCount > 0)
                                .map(event -> new TimedCount(event.finishedAt, event.duplicateCount))
                                .toList(),
                        fromInclusive,
                        toExclusive,
                        zoneId)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(event -> "ERROR".equals(event.status))
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive,
                        zoneId)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(event -> !"scheduler".equals(event.triggerName) && !"idle-source".equals(event.triggerName))
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive,
                        zoneId)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(event -> "scheduler".equals(event.triggerName))
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive,
                        zoneId)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(event -> "idle-source".equals(event.triggerName))
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive,
                        zoneId)));
    }

    public List<ImportTimelinePointView> buildRecentDailyTimeline(List<TimedCount> values, int days, ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        LocalDate start = today.minusDays(days - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(zoneId).toLocalDate();
            if (!bucket.isBefore(start) && !bucket.isAfter(today)) {
                counts.merge(bucket, value.count(), Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate bucket = start.plusDays(offset);
            points.add(new ImportTimelinePointView(bucket.toString(), counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildTodayTimeline(List<TimedCount> values, ZoneId zoneId) {
        return buildRecentHourlyTimeline(values, HOURLY_BUCKETS, zoneId);
    }

    private List<ImportTimelinePointView> buildYesterdayTimeline(List<TimedCount> values, ZoneId zoneId) {
        return buildHourlyRangeTimeline(values, LocalDate.now(zoneId).minusDays(1), zoneId);
    }

    private List<ImportTimelinePointView> buildHourlyRangeTimeline(List<TimedCount> values, LocalDate day, ZoneId zoneId) {
        Map<Integer, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDateTime timestamp = value.at().atZone(zoneId).toLocalDateTime();
            if (timestamp.toLocalDate().equals(day)) {
                counts.merge(timestamp.getHour(), value.count(), Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int hour = 0; hour < HOURLY_BUCKETS; hour++) {
            points.add(new ImportTimelinePointView(String.format("%02d:00", hour), counts.getOrDefault(hour, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildPastWeekTimeline(List<TimedCount> values, ZoneId zoneId) {
        return buildRecentHourlyTimeline(values, PAST_WEEK_HOURS, zoneId);
    }

    private List<ImportTimelinePointView> buildPastMonthTimeline(List<TimedCount> values, ZoneId zoneId) {
        return buildRecentDailyTimeline(values, 30, zoneId);
    }

    private List<ImportTimelinePointView> buildPastTrimesterTimeline(List<TimedCount> values, ZoneId zoneId) {
        return buildRecentWeeklyTimeline(values, TRIMESTER_WEEKS, zoneId);
    }

    private List<ImportTimelinePointView> buildPastSemesterTimeline(List<TimedCount> values, ZoneId zoneId) {
        return buildRecentWeeklyTimeline(values, SEMESTER_WEEKS, zoneId);
    }

    private List<ImportTimelinePointView> buildPastYearTimeline(List<TimedCount> values, ZoneId zoneId) {
        return buildRecentWeeklyTimeline(values, YEAR_WEEKS, zoneId);
    }

    private List<ImportTimelinePointView> buildRecentHourlyTimeline(List<TimedCount> values, int hours, ZoneId zoneId) {
        LocalDateTime end = LocalDateTime.now(zoneId).truncatedTo(ChronoUnit.HOURS);
        LocalDateTime start = end.minusHours(hours - 1L);
        Map<LocalDateTime, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDateTime bucket = value.at().atZone(zoneId).toLocalDateTime().truncatedTo(ChronoUnit.HOURS);
            if (!bucket.isBefore(start) && !bucket.isAfter(end)) {
                counts.merge(bucket, value.count(), Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (LocalDateTime bucket = start; !bucket.isAfter(end); bucket = bucket.plusHours(1)) {
            points.add(new ImportTimelinePointView(
                    String.format("%sT%02d:00", bucket.toLocalDate(), bucket.getHour()),
                    counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildRecentWeeklyTimeline(List<TimedCount> values, int weeks, ZoneId zoneId) {
        LocalDate currentWeekStart = LocalDate.now(zoneId).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate start = currentWeekStart.minusWeeks(weeks - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(zoneId).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (!bucket.isBefore(start) && !bucket.isAfter(currentWeekStart)) {
                counts.merge(bucket, value.count(), Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int offset = 0; offset < weeks; offset++) {
            LocalDate bucket = start.plusWeeks(offset);
            points.add(new ImportTimelinePointView(weekBucketLabel(bucket), counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private String weekBucketLabel(LocalDate bucket) {
        return String.format(
                "%d-W%02d",
                bucket.get(IsoFields.WEEK_BASED_YEAR),
                bucket.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private List<ImportTimelinePointView> buildCustomTimeline(
            List<TimedCount> values,
            Instant fromInclusive,
            Instant toExclusive,
            ZoneId zoneId) {
        long hours = Math.max(1L, ChronoUnit.HOURS.between(fromInclusive, toExclusive));
        long days = Math.max(1L, ChronoUnit.DAYS.between(fromInclusive, toExclusive));
        if (hours <= 168L) {
            return buildHourlyCustomTimeline(values, fromInclusive, toExclusive, zoneId);
        }
        if (days <= 180L) {
            return buildDailyCustomTimeline(values, fromInclusive, toExclusive, zoneId);
        }
        return buildMonthlyCustomTimeline(values, fromInclusive, toExclusive, zoneId);
    }

    private List<ImportTimelinePointView> buildHourlyCustomTimeline(
            List<TimedCount> values,
            Instant fromInclusive,
            Instant toExclusive,
            ZoneId zoneId) {
        LocalDateTime start = fromInclusive.atZone(zoneId).toLocalDateTime().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime end = toExclusive.atZone(zoneId).toLocalDateTime().truncatedTo(ChronoUnit.HOURS);
        Map<LocalDateTime, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDateTime bucket = value.at().atZone(zoneId).toLocalDateTime().truncatedTo(ChronoUnit.HOURS);
            if (!bucket.isBefore(start) && !bucket.isAfter(end)) {
                counts.merge(bucket, value.count(), Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (LocalDateTime bucket = start; !bucket.isAfter(end); bucket = bucket.plusHours(1)) {
            points.add(new ImportTimelinePointView(
                    String.format("%s %02d:00", bucket.toLocalDate(), bucket.getHour()),
                    counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildDailyCustomTimeline(
            List<TimedCount> values,
            Instant fromInclusive,
            Instant toExclusive,
            ZoneId zoneId) {
        LocalDate start = fromInclusive.atZone(zoneId).toLocalDate();
        LocalDate end = toExclusive.minusMillis(1).atZone(zoneId).toLocalDate();
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(zoneId).toLocalDate();
            if (!bucket.isBefore(start) && !bucket.isAfter(end)) {
                counts.merge(bucket, value.count(), Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (LocalDate bucket = start; !bucket.isAfter(end); bucket = bucket.plusDays(1)) {
            points.add(new ImportTimelinePointView(bucket.toString(), counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildMonthlyCustomTimeline(
            List<TimedCount> values,
            Instant fromInclusive,
            Instant toExclusive,
            ZoneId zoneId) {
        LocalDate start = fromInclusive.atZone(zoneId).toLocalDate().withDayOfMonth(1);
        LocalDate end = toExclusive.minusMillis(1).atZone(zoneId).toLocalDate().withDayOfMonth(1);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(zoneId).toLocalDate().withDayOfMonth(1);
            if (!bucket.isBefore(start) && !bucket.isAfter(end)) {
                counts.merge(bucket, value.count(), Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (LocalDate bucket = start; !bucket.isAfter(end); bucket = bucket.plusMonths(1)) {
            points.add(new ImportTimelinePointView(
                    String.format("%d-%02d", bucket.getYear(), bucket.getMonthValue()),
                    counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    public record TimedCount(Instant at, long count) {
    }
}
