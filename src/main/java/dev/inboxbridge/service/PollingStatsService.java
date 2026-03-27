package dev.inboxbridge.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.inboxbridge.dto.GlobalPollingStatsView;
import dev.inboxbridge.dto.ImportTimelinePointView;
import dev.inboxbridge.dto.UserPollingStatsView;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserBridge;
import dev.inboxbridge.persistence.UserBridgeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Builds import/polling statistics for the global admin dashboard and for
 * user-scoped poller settings views.
 */
@ApplicationScoped
public class PollingStatsService {

    private static final int DEFAULT_TIMELINE_DAYS = 7;
    private static final int HOURLY_BUCKETS = 24;
    private static final int DAILY_BUCKETS = 30;
    private static final int MONTHLY_BUCKETS = 12;
    private static final int TRIMESTER_BUCKETS = 4;
    private static final int SEMESTER_BUCKETS = 2;
    private static final int YEARLY_BUCKETS = 5;

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    UserBridgeRepository userBridgeRepository;

    @Inject
    SourcePollEventService sourcePollEventService;

    @Inject
    EnvSourceService envSourceService;

    public GlobalPollingStatsView globalStats(int sourcesWithErrors) {
        int envConfigured = envSourceService.configuredSources().size();
        int envEnabled = (int) envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .filter(dev.inboxbridge.config.BridgeConfig.Source::enabled)
                .count();
        int userConfigured = (int) userBridgeRepository.count();
        int userEnabled = (int) userBridgeRepository.count("enabled", true);
        Map<String, List<ImportTimelinePointView>> timelines = buildTimelines(
                importedMessageRepository.listImportedAtSince(earliestTimelineInstant()));
        return new GlobalPollingStatsView(
                importedMessageRepository.count(),
                envConfigured + userConfigured,
                envEnabled + userEnabled,
                sourcesWithErrors,
                buildTimeline(importedMessageRepository.summarizeByImportedDay(), DEFAULT_TIMELINE_DAYS),
                timelines);
    }

    public UserPollingStatsView userStats(Long userId) {
        List<UserBridge> bridges = userBridgeRepository.listByUserId(userId);
        int enabled = (int) bridges.stream().filter(bridge -> bridge.enabled).count();
        int sourcesWithErrors = (int) bridges.stream()
                .map(bridge -> sourcePollEventService.latestForSource(bridge.bridgeId).orElse(null))
                .filter(event -> event != null && "ERROR".equals(event.status()))
                .count();
        String destinationKey = "user-gmail:" + userId;
        Map<String, List<ImportTimelinePointView>> timelines = buildTimelines(
                importedMessageRepository.listImportedAtSinceForDestinationKey(destinationKey, earliestTimelineInstant()));
        return new UserPollingStatsView(
                importedMessageRepository.countByDestinationKey(destinationKey),
                bridges.size(),
                enabled,
                sourcesWithErrors,
                buildTimeline(importedMessageRepository.summarizeByImportedDayForDestinationKey(destinationKey), DEFAULT_TIMELINE_DAYS),
                timelines);
    }

    private Instant earliestTimelineInstant() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .withMonth(Month.JANUARY.getValue())
                .withDayOfMonth(1)
                .minusYears(YEARLY_BUCKETS - 1L)
                .toInstant(ZoneOffset.UTC);
    }

    private Map<String, List<ImportTimelinePointView>> buildTimelines(List<Instant> importedAtValues) {
        Map<String, List<ImportTimelinePointView>> timelines = new LinkedHashMap<>();
        timelines.put("today", buildTodayTimeline(importedAtValues));
        timelines.put("yesterday", buildYesterdayTimeline(importedAtValues));
        timelines.put("pastWeek", buildPastWeekTimeline(importedAtValues));
        timelines.put("pastMonth", buildPastMonthTimeline(importedAtValues));
        timelines.put("pastTrimester", buildPastTrimesterTimeline(importedAtValues));
        timelines.put("pastSemester", buildPastSemesterTimeline(importedAtValues));
        timelines.put("pastYear", buildPastYearTimeline(importedAtValues));
        timelines.put("hour", buildHourlyTimeline(importedAtValues));
        timelines.put("day", buildDailyTimeline(importedAtValues));
        timelines.put("month", buildMonthlyTimeline(importedAtValues));
        timelines.put("trimester", buildQuarterTimeline(importedAtValues));
        timelines.put("semester", buildSemesterTimeline(importedAtValues));
        timelines.put("year", buildYearTimeline(importedAtValues));
        return timelines;
    }

    private List<ImportTimelinePointView> buildTodayTimeline(List<Instant> importedAtValues) {
        return buildHourlyRangeTimeline(importedAtValues, LocalDate.now(ZoneOffset.UTC));
    }

    private List<ImportTimelinePointView> buildYesterdayTimeline(List<Instant> importedAtValues) {
        return buildHourlyRangeTimeline(importedAtValues, LocalDate.now(ZoneOffset.UTC).minusDays(1));
    }

    private List<ImportTimelinePointView> buildHourlyRangeTimeline(List<Instant> importedAtValues, LocalDate day) {
        Map<Integer, Long> counts = new HashMap<>();
        for (Instant importedAt : importedAtValues) {
            LocalDateTime timestamp = importedAt.atZone(ZoneOffset.UTC).toLocalDateTime();
            if (timestamp.toLocalDate().equals(day)) {
                counts.merge(timestamp.getHour(), 1L, Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int hour = 0; hour < HOURLY_BUCKETS; hour++) {
            points.add(new ImportTimelinePointView(String.format("%02d:00", hour), counts.getOrDefault(hour, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildPastWeekTimeline(List<Instant> importedAtValues) {
        return buildRecentDailyTimeline(importedAtValues, 7);
    }

    private List<ImportTimelinePointView> buildPastMonthTimeline(List<Instant> importedAtValues) {
        return buildRecentDailyTimeline(importedAtValues, 30);
    }

    private List<ImportTimelinePointView> buildRecentDailyTimeline(List<Instant> importedAtValues, int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(days - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Instant importedAt : importedAtValues) {
            LocalDate bucket = importedAt.atZone(ZoneOffset.UTC).toLocalDate();
            if (!bucket.isBefore(start) && !bucket.isAfter(today)) {
                counts.merge(bucket, 1L, Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate bucket = start.plusDays(offset);
            points.add(new ImportTimelinePointView(bucket.toString(), counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildPastTrimesterTimeline(List<Instant> importedAtValues) {
        return buildRecentMonthlyTimeline(importedAtValues, 3);
    }

    private List<ImportTimelinePointView> buildPastSemesterTimeline(List<Instant> importedAtValues) {
        return buildRecentMonthlyTimeline(importedAtValues, 6);
    }

    private List<ImportTimelinePointView> buildPastYearTimeline(List<Instant> importedAtValues) {
        return buildRecentMonthlyTimeline(importedAtValues, 12);
    }

    private List<ImportTimelinePointView> buildRecentMonthlyTimeline(List<Instant> importedAtValues, int months) {
        LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate start = currentMonth.minusMonths(months - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Instant importedAt : importedAtValues) {
            LocalDate bucket = importedAt.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
            if (!bucket.isBefore(start) && !bucket.isAfter(currentMonth)) {
                counts.merge(bucket, 1L, Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int offset = 0; offset < months; offset++) {
            LocalDate bucket = start.plusMonths(offset);
            points.add(new ImportTimelinePointView(
                    String.format("%d-%02d", bucket.getYear(), bucket.getMonthValue()),
                    counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildHourlyTimeline(List<Instant> importedAtValues) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        Map<LocalDateTime, Long> counts = new HashMap<>();
        LocalDateTime start = now.minusHours(HOURLY_BUCKETS - 1L);
        for (Instant importedAt : importedAtValues) {
            LocalDateTime bucket = importedAt.atZone(ZoneOffset.UTC).toLocalDateTime()
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, 1L, Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int offset = 0; offset < HOURLY_BUCKETS; offset++) {
            LocalDateTime bucket = start.plusHours(offset);
            points.add(new ImportTimelinePointView(
                    String.format("%02d:00", bucket.getHour()),
                    counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildDailyTimeline(List<Instant> importedAtValues) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(DAILY_BUCKETS - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Instant importedAt : importedAtValues) {
            LocalDate bucket = importedAt.atZone(ZoneOffset.UTC).toLocalDate();
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, 1L, Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int offset = 0; offset < DAILY_BUCKETS; offset++) {
            LocalDate bucket = start.plusDays(offset);
            points.add(new ImportTimelinePointView(bucket.toString(), counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildMonthlyTimeline(List<Instant> importedAtValues) {
        LocalDate monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        Map<LocalDate, Long> counts = new HashMap<>();
        LocalDate start = monthStart.minusMonths(MONTHLY_BUCKETS - 1L);
        for (Instant importedAt : importedAtValues) {
            LocalDate bucket = importedAt.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, 1L, Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int offset = 0; offset < MONTHLY_BUCKETS; offset++) {
            LocalDate bucket = start.plusMonths(offset);
            points.add(new ImportTimelinePointView(
                    String.format("%d-%02d", bucket.getYear(), bucket.getMonthValue()),
                    counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildQuarterTimeline(List<Instant> importedAtValues) {
        QuarterBucket current = QuarterBucket.from(LocalDate.now(ZoneOffset.UTC));
        QuarterBucket start = current.minusQuarters(TRIMESTER_BUCKETS - 1);
        Map<QuarterBucket, Long> counts = new HashMap<>();
        for (Instant importedAt : importedAtValues) {
            QuarterBucket bucket = QuarterBucket.from(importedAt.atZone(ZoneOffset.UTC).toLocalDate());
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, 1L, Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        QuarterBucket bucket = start;
        for (int index = 0; index < TRIMESTER_BUCKETS; index++) {
            points.add(new ImportTimelinePointView(bucket.label(), counts.getOrDefault(bucket, 0L)));
            bucket = bucket.plusQuarters(1);
        }
        return points;
    }

    private List<ImportTimelinePointView> buildSemesterTimeline(List<Instant> importedAtValues) {
        SemesterBucket current = SemesterBucket.from(LocalDate.now(ZoneOffset.UTC));
        SemesterBucket start = current.minusSemesters(SEMESTER_BUCKETS - 1);
        Map<SemesterBucket, Long> counts = new HashMap<>();
        for (Instant importedAt : importedAtValues) {
            SemesterBucket bucket = SemesterBucket.from(importedAt.atZone(ZoneOffset.UTC).toLocalDate());
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, 1L, Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        SemesterBucket bucket = start;
        for (int index = 0; index < SEMESTER_BUCKETS; index++) {
            points.add(new ImportTimelinePointView(bucket.label(), counts.getOrDefault(bucket, 0L)));
            bucket = bucket.plusSemesters(1);
        }
        return points;
    }

    private List<ImportTimelinePointView> buildYearTimeline(List<Instant> importedAtValues) {
        int currentYear = LocalDate.now(ZoneOffset.UTC).getYear();
        int startYear = currentYear - YEARLY_BUCKETS + 1;
        Map<Integer, Long> counts = new HashMap<>();
        for (Instant importedAt : importedAtValues) {
            int bucket = importedAt.atZone(ZoneOffset.UTC).getYear();
            if (bucket >= startYear) {
                counts.merge(bucket, 1L, Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int year = startYear; year <= currentYear; year++) {
            points.add(new ImportTimelinePointView(Integer.toString(year), counts.getOrDefault(year, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildTimeline(List<Object[]> rows, int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(days - 1L);
        Map<LocalDate, Long> countsByDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate day = toUtcDate(row[0]);
            Number count = (Number) row[1];
            if (day != null && !day.isBefore(start)) {
                countsByDay.put(day, count.longValue());
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate day = start.plusDays(offset);
            points.add(new ImportTimelinePointView(
                    day.toString(),
                    countsByDay.getOrDefault(day, 0L)));
        }
        return points;
    }

    private LocalDate toUtcDate(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return null;
    }

    private record QuarterBucket(int year, int quarter) {
        private static QuarterBucket from(LocalDate date) {
            return new QuarterBucket(date.getYear(), ((date.getMonthValue() - 1) / 3) + 1);
        }

        private QuarterBucket plusQuarters(int amount) {
            int zeroBased = (year * 4) + (quarter - 1) + amount;
            return new QuarterBucket(Math.floorDiv(zeroBased, 4), Math.floorMod(zeroBased, 4) + 1);
        }

        private QuarterBucket minusQuarters(int amount) {
            return plusQuarters(-amount);
        }

        private boolean isBefore(QuarterBucket other) {
            return year < other.year || (year == other.year && quarter < other.quarter);
        }

        private String label() {
            return year + "-Q" + quarter;
        }
    }

    private record SemesterBucket(int year, int semester) {
        private static SemesterBucket from(LocalDate date) {
            return new SemesterBucket(date.getYear(), date.getMonthValue() <= 6 ? 1 : 2);
        }

        private SemesterBucket plusSemesters(int amount) {
            int zeroBased = (year * 2) + (semester - 1) + amount;
            return new SemesterBucket(Math.floorDiv(zeroBased, 2), Math.floorMod(zeroBased, 2) + 1);
        }

        private SemesterBucket minusSemesters(int amount) {
            return plusSemesters(-amount);
        }

        private boolean isBefore(SemesterBucket other) {
            return year < other.year || (year == other.year && semester < other.semester);
        }

        private String label() {
            return year + "-S" + semester;
        }
    }
}
