package dev.inboxbridge.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.GlobalPollingStatsView;
import dev.inboxbridge.dto.ImportTimelinePointView;
import dev.inboxbridge.dto.PollingTimelineBundleView;
import dev.inboxbridge.dto.PollingBreakdownItemView;
import dev.inboxbridge.dto.PollingHealthSummaryView;
import dev.inboxbridge.dto.SourcePollingStatsView;
import dev.inboxbridge.dto.SourcePollingStateView;
import dev.inboxbridge.dto.UserPollingStatsView;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.SourcePollEvent;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
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
    private static final int PAST_WEEK_HOURS = 7 * 24;
    private static final int TRIMESTER_WEEKS = 13;
    private static final int SEMESTER_WEEKS = 26;
    private static final int YEAR_WEEKS = 52;

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    UserMailDestinationConfigRepository userMailDestinationConfigRepository;

    @Inject
    SourcePollEventService sourcePollEventService;

    @Inject
    EnvSourceService envSourceService;

    @Inject
    SourcePollingStateService sourcePollingStateService;

    public GlobalPollingStatsView globalStats(int sourcesWithErrors) {
        return globalStats(sourcesWithErrors, ZoneOffset.UTC);
    }

    public GlobalPollingStatsView globalStats(int sourcesWithErrors, ZoneId zoneId) {
        ZoneId effectiveZoneId = effectiveZoneId(zoneId);
        List<ConfiguredSourceSnapshot> sources = new ArrayList<>();
        for (EnvSourceService.IndexedSource indexedSource : envSourceService.configuredSources()) {
            dev.inboxbridge.config.InboxBridgeConfig.Source source = indexedSource.source();
            sources.add(new ConfiguredSourceSnapshot(
                    source.id(),
                    source.enabled(),
                    source.protocol(),
                    source.authMethod(),
                    source.oauthProvider(),
                    source.host()));
        }
        for (UserEmailAccount emailAccount : userEmailAccountRepository.listAll()) {
            sources.add(new ConfiguredSourceSnapshot(
                emailAccount.emailAccountId,
                emailAccount.enabled,
                emailAccount.protocol,
                emailAccount.authMethod,
                emailAccount.oauthProvider,
                emailAccount.host));
        }
        Instant earliest = earliestTimelineInstant();
        List<Instant> imports = importedMessageRepository.listImportedAtSince(earliest);
        Map<String, List<ImportTimelinePointView>> timelines = buildTimelinesFromInstants(imports, effectiveZoneId);
        List<SourcePollEvent> events = sourcePollEventService.listSince(earliest);
        PollingStatsComputation computation = computeEventStats(sources, events, sourcesWithErrors);
        return new GlobalPollingStatsView(
                importedMessageRepository.count(),
                sources.size(),
                (int) sources.stream().filter(ConfiguredSourceSnapshot::enabled).count(),
                computation.sourcesWithErrors(),
                computation.errorPolls(),
                buildRecentDailyTimeline(toTimedCounts(imports), DEFAULT_TIMELINE_DAYS, effectiveZoneId),
                timelines,
                buildTimelinesFromTimedCounts(computation.duplicatePoints(), effectiveZoneId),
                buildTimelinesFromTimedCounts(computation.errorPoints(), effectiveZoneId),
                buildTimelinesFromTimedCounts(computation.manualRunPoints(), effectiveZoneId),
                buildTimelinesFromTimedCounts(computation.scheduledRunPoints(), effectiveZoneId),
                buildTimelinesFromTimedCounts(computation.idleRunPoints(), effectiveZoneId),
                computation.health(),
                computation.providerBreakdown(),
                computation.manualRuns(),
                computation.scheduledRuns(),
                computation.idleRuns(),
                computation.averagePollDurationMillis());
    }

    public UserPollingStatsView userStats(Long userId) {
        return userStats(userId, ZoneOffset.UTC);
    }

    public UserPollingStatsView userStats(Long userId, ZoneId zoneId) {
        ZoneId effectiveZoneId = effectiveZoneId(zoneId);
        List<UserEmailAccount> emailAccounts = userEmailAccountRepository.listByUserId(userId);
        List<ConfiguredSourceSnapshot> sources = emailAccounts.stream()
            .map(emailAccount -> new ConfiguredSourceSnapshot(
                emailAccount.emailAccountId,
                emailAccount.enabled,
                emailAccount.protocol,
                emailAccount.authMethod,
                emailAccount.oauthProvider,
                emailAccount.host))
                .toList();
        String destinationKey = "user-gmail:" + userId;
        Instant earliest = earliestTimelineInstant();
        List<Instant> imports = importedMessageRepository.listImportedAtSinceForDestinationKey(destinationKey, earliest);
        Map<String, List<ImportTimelinePointView>> timelines = buildTimelinesFromInstants(imports, effectiveZoneId);
        List<SourcePollEvent> events = sourcePollEventService.listBySourceIdsSince(
                sources.stream().map(ConfiguredSourceSnapshot::sourceId).toList(),
                earliest);
        ScopedStatsData scopedStats = buildScopedStats(
                sources,
                importedMessageRepository.countByDestinationKey(destinationKey),
                buildRecentDailyTimeline(toTimedCounts(imports), DEFAULT_TIMELINE_DAYS, effectiveZoneId),
                timelines,
                events,
                -1,
                effectiveZoneId);
        return new UserPollingStatsView(
                scopedStats.totalImportedMessages(),
                scopedStats.configuredMailFetchers(),
                scopedStats.enabledMailFetchers(),
                scopedStats.sourcesWithErrors(),
                scopedStats.errorPolls(),
                scopedStats.importsByDay(),
                scopedStats.importTimelines(),
                scopedStats.duplicateTimelines(),
                scopedStats.errorTimelines(),
                scopedStats.manualRunTimelines(),
                scopedStats.scheduledRunTimelines(),
                scopedStats.idleRunTimelines(),
                scopedStats.health(),
                scopedStats.providerBreakdown(),
                scopedStats.manualRuns(),
                scopedStats.scheduledRuns(),
                scopedStats.idleRuns(),
                scopedStats.averagePollDurationMillis());
    }

    public SourcePollingStatsView sourceStats(RuntimeEmailAccount emailAccount) {
        return sourceStats(emailAccount, ZoneOffset.UTC);
    }

    public SourcePollingStatsView sourceStats(RuntimeEmailAccount emailAccount, ZoneId zoneId) {
        ZoneId effectiveZoneId = effectiveZoneId(zoneId);
        Instant earliest = earliestTimelineInstant();
        ConfiguredSourceSnapshot source = new ConfiguredSourceSnapshot(
            emailAccount.id(),
            emailAccount.enabled(),
            emailAccount.protocol(),
            emailAccount.authMethod(),
            emailAccount.oauthProvider(),
            emailAccount.host());
        String destinationKey = emailAccount.destination() != null ? emailAccount.destination().subjectKey() : null;
        long totalImportedMessages = destinationKey == null
                ? 0L
            : importedMessageRepository.countByDestinationKeyAndSourceAccountId(destinationKey, emailAccount.id());
        List<Instant> imports = destinationKey == null
                ? List.of()
                : importedMessageRepository.listImportedAtSinceForDestinationKeyAndSourceAccountId(destinationKey, emailAccount.id(), earliest);
        List<ImportTimelinePointView> importsByDay = buildRecentDailyTimeline(toTimedCounts(imports), DEFAULT_TIMELINE_DAYS, effectiveZoneId);
        Map<String, List<ImportTimelinePointView>> timelines = destinationKey == null
                ? buildTimelinesFromInstants(List.of(), effectiveZoneId)
                : buildTimelinesFromInstants(imports, effectiveZoneId);
        List<SourcePollEvent> events = sourcePollEventService.listBySourceIdsSince(List.of(emailAccount.id()), earliest);
        ScopedStatsData scopedStats = buildScopedStats(
                List.of(source),
                totalImportedMessages,
                importsByDay,
                timelines,
                events,
                -1,
                effectiveZoneId);
        return new SourcePollingStatsView(
                scopedStats.totalImportedMessages(),
                scopedStats.configuredMailFetchers(),
                scopedStats.enabledMailFetchers(),
                scopedStats.sourcesWithErrors(),
                scopedStats.errorPolls(),
                scopedStats.importsByDay(),
                scopedStats.importTimelines(),
                scopedStats.duplicateTimelines(),
                scopedStats.errorTimelines(),
                scopedStats.manualRunTimelines(),
                scopedStats.scheduledRunTimelines(),
                scopedStats.idleRunTimelines(),
                scopedStats.health(),
                scopedStats.providerBreakdown(),
                scopedStats.manualRuns(),
                scopedStats.scheduledRuns(),
                scopedStats.idleRuns(),
                scopedStats.averagePollDurationMillis());
    }

    public PollingTimelineBundleView globalTimelineBundle(Instant fromInclusive, Instant toExclusive) {
        return globalTimelineBundle(fromInclusive, toExclusive, ZoneOffset.UTC);
    }

    public PollingTimelineBundleView globalTimelineBundle(Instant fromInclusive, Instant toExclusive, ZoneId zoneId) {
        Instant normalizedTo = normalizeUpperBound(toExclusive);
        List<Instant> imports = importedMessageRepository.listImportedAtSince(fromInclusive).stream()
                .filter(value -> value.isBefore(normalizedTo))
                .toList();
        List<SourcePollEvent> events = sourcePollEventService.listSince(fromInclusive).stream()
                .filter(event -> event.finishedAt.isBefore(normalizedTo))
                .toList();
        return buildTimelineBundle(imports, events, fromInclusive, normalizedTo, effectiveZoneId(zoneId));
    }

    public PollingTimelineBundleView userTimelineBundle(Long userId, Instant fromInclusive, Instant toExclusive) {
        return userTimelineBundle(userId, fromInclusive, toExclusive, ZoneOffset.UTC);
    }

    public PollingTimelineBundleView userTimelineBundle(Long userId, Instant fromInclusive, Instant toExclusive, ZoneId zoneId) {
        Instant normalizedTo = normalizeUpperBound(toExclusive);
        String destinationKey = userMailDestinationConfigRepository.findByUserId(userId)
            .map(config -> UserMailDestinationConfigService.PROVIDER_GMAIL.equals(config.provider) ? "user-gmail:" + userId : "user-destination:" + userId)
            .orElse("user-gmail:" + userId);
        List<Instant> imports = importedMessageRepository.listImportedAtSinceForDestinationKey(destinationKey, fromInclusive).stream()
                .filter(value -> value.isBefore(normalizedTo))
                .toList();
        List<String> sourceIds = userEmailAccountRepository.listByUserId(userId).stream()
            .map(emailAccount -> emailAccount.emailAccountId)
                .toList();
        List<SourcePollEvent> events = sourcePollEventService.listBySourceIdsSince(sourceIds, fromInclusive).stream()
                .filter(event -> event.finishedAt.isBefore(normalizedTo))
                .toList();
        return buildTimelineBundle(imports, events, fromInclusive, normalizedTo, effectiveZoneId(zoneId));
    }

    public PollingTimelineBundleView sourceTimelineBundle(RuntimeEmailAccount emailAccount, Instant fromInclusive, Instant toExclusive) {
        return sourceTimelineBundle(emailAccount, fromInclusive, toExclusive, ZoneOffset.UTC);
    }

    public PollingTimelineBundleView sourceTimelineBundle(RuntimeEmailAccount emailAccount, Instant fromInclusive, Instant toExclusive, ZoneId zoneId) {
        Instant normalizedTo = normalizeUpperBound(toExclusive);
        String destinationKey = emailAccount.destination() != null ? emailAccount.destination().subjectKey() : null;
        List<Instant> imports = destinationKey == null
                ? List.of()
            : importedMessageRepository.listImportedAtSinceForDestinationKeyAndSourceAccountId(destinationKey, emailAccount.id(), fromInclusive).stream()
                        .filter(value -> value.isBefore(normalizedTo))
                        .toList();
        List<SourcePollEvent> events = sourcePollEventService.listBySourceIdsSince(List.of(emailAccount.id()), fromInclusive).stream()
                .filter(event -> event.finishedAt.isBefore(normalizedTo))
                .toList();
        return buildTimelineBundle(imports, events, fromInclusive, normalizedTo, effectiveZoneId(zoneId));
    }

    private ScopedStatsData buildScopedStats(
            List<ConfiguredSourceSnapshot> sources,
            long totalImportedMessages,
            List<ImportTimelinePointView> importsByDay,
            Map<String, List<ImportTimelinePointView>> importTimelines,
            List<SourcePollEvent> events,
            int fallbackSourcesWithErrors,
            ZoneId zoneId) {
        PollingStatsComputation computation = computeEventStats(sources, events, fallbackSourcesWithErrors);
        return new ScopedStatsData(
                totalImportedMessages,
                sources.size(),
                (int) sources.stream().filter(ConfiguredSourceSnapshot::enabled).count(),
                computation.sourcesWithErrors(),
                computation.errorPolls(),
                importsByDay,
                importTimelines,
                buildTimelinesFromTimedCounts(computation.duplicatePoints(), zoneId),
                buildTimelinesFromTimedCounts(computation.errorPoints(), zoneId),
                buildTimelinesFromTimedCounts(computation.manualRunPoints(), zoneId),
                buildTimelinesFromTimedCounts(computation.scheduledRunPoints(), zoneId),
                buildTimelinesFromTimedCounts(computation.idleRunPoints(), zoneId),
                computation.health(),
                computation.providerBreakdown(),
                computation.manualRuns(),
                computation.scheduledRuns(),
                computation.idleRuns(),
                computation.averagePollDurationMillis());
    }

    private PollingTimelineBundleView buildTimelineBundle(
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
                                .filter(this::isManualTrigger)
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive,
                        zoneId)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(this::isScheduledTrigger)
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive,
                        zoneId)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(this::isIdleTrigger)
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive,
                        zoneId)));
    }

    private Instant normalizeUpperBound(Instant value) {
        return value == null ? Instant.now() : value;
    }

    private Instant earliestTimelineInstant() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .withMonth(Month.JANUARY.getValue())
                .withDayOfMonth(1)
                .minusYears(1L)
                .toInstant(ZoneOffset.UTC);
    }

    private ZoneId effectiveZoneId(ZoneId zoneId) {
        return zoneId == null ? ZoneOffset.UTC : zoneId;
    }

    private Map<String, List<ImportTimelinePointView>> buildTimelinesFromInstants(List<Instant> importedAtValues, ZoneId zoneId) {
        return buildTimelinesFromTimedCounts(toTimedCounts(importedAtValues), zoneId);
    }

    private Map<String, List<ImportTimelinePointView>> buildTimelinesFromTimedCounts(List<TimedCount> values, ZoneId zoneId) {
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

    private List<TimedCount> toTimedCounts(List<Instant> instants) {
        return instants.stream()
                .map(value -> new TimedCount(value, 1L))
                .toList();
    }

    private List<ImportTimelinePointView> buildTodayTimeline(List<TimedCount> values, ZoneId zoneId) {
        return buildHourlyRangeTimeline(values, LocalDate.now(zoneId), zoneId);
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

    private List<ImportTimelinePointView> buildRecentDailyTimeline(List<TimedCount> values, int days, ZoneId zoneId) {
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
            points.add(new ImportTimelinePointView(
                    weekBucketLabel(bucket),
                    counts.getOrDefault(bucket, 0L)));
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
        LocalDateTime start = fromInclusive.atZone(zoneId).toLocalDateTime()
                .truncatedTo(ChronoUnit.HOURS);
        LocalDateTime end = toExclusive.atZone(zoneId).toLocalDateTime()
                .truncatedTo(ChronoUnit.HOURS);
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

    private List<ImportTimelinePointView> buildRecentMonthlyTimeline(List<TimedCount> values, int months, ZoneId zoneId) {
        LocalDate currentMonth = LocalDate.now(zoneId).withDayOfMonth(1);
        LocalDate start = currentMonth.minusMonths(months - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(zoneId).toLocalDate().withDayOfMonth(1);
            if (!bucket.isBefore(start) && !bucket.isAfter(currentMonth)) {
                counts.merge(bucket, value.count(), Long::sum);
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

    private PollingStatsComputation computeEventStats(
            List<ConfiguredSourceSnapshot> sources,
            List<SourcePollEvent> events,
            int fallbackSourcesWithErrors) {
        Instant now = Instant.now();
        Map<String, SourcePollingStateView> stateBySource = sourcePollingStateService.viewBySourceIds(
                sources.stream().map(ConfiguredSourceSnapshot::sourceId).toList());
        Map<String, AdminPollEventSummary> latestEvents = new HashMap<>();
        for (ConfiguredSourceSnapshot source : sources) {
            Optional<AdminPollEventSummary> latest = sourcePollEventService.latestForSource(source.sourceId());
            latest.ifPresent(summary -> latestEvents.put(source.sourceId(), summary));
        }

        int active = 0;
        int coolingDown = 0;
        int failing = 0;
        int disabled = 0;
        Map<String, Long> providerCounts = new HashMap<>();
        for (ConfiguredSourceSnapshot source : sources) {
            providerCounts.merge(providerLabel(source), 1L, Long::sum);
            if (!source.enabled()) {
                disabled++;
                continue;
            }
            SourcePollingStateView state = stateBySource.get(source.sourceId());
            AdminPollEventSummary latestEvent = latestEvents.get(source.sourceId());
            if (state != null && state.cooldownUntil() != null && now.isBefore(state.cooldownUntil())) {
                coolingDown++;
            } else if (latestEvent != null && "ERROR".equals(latestEvent.status())) {
                failing++;
            } else {
                active++;
            }
        }

        long manualRuns = events.stream()
                .filter(this::isManualTrigger)
                .count();
        long scheduledRuns = events.stream()
                .filter(this::isScheduledTrigger)
                .count();
        long idleRuns = events.stream()
                .filter(this::isIdleTrigger)
                .count();
        long errorPolls = events.stream()
                .filter(event -> "ERROR".equals(event.status))
                .count();
        long averagePollDurationMillis = events.isEmpty() ? 0L : Math.round(events.stream()
                .mapToLong(event -> Math.max(0L, event.finishedAt.toEpochMilli() - event.startedAt.toEpochMilli()))
                .average()
                .orElse(0D));
        List<TimedCount> duplicatePoints = events.stream()
                .filter(event -> event.duplicateCount > 0)
                .map(event -> new TimedCount(event.finishedAt, event.duplicateCount))
                .toList();
        List<TimedCount> errorPoints = events.stream()
                .filter(event -> "ERROR".equals(event.status))
                .map(event -> new TimedCount(event.finishedAt, 1L))
                .toList();
        List<TimedCount> manualRunPoints = events.stream()
                .filter(this::isManualTrigger)
                .map(event -> new TimedCount(event.finishedAt, 1L))
                .toList();
        List<TimedCount> scheduledRunPoints = events.stream()
                .filter(this::isScheduledTrigger)
                .map(event -> new TimedCount(event.finishedAt, 1L))
                .toList();
        List<TimedCount> idleRunPoints = events.stream()
                .filter(this::isIdleTrigger)
                .map(event -> new TimedCount(event.finishedAt, 1L))
                .toList();
        int derivedSourcesWithErrors = failing + coolingDown;
        return new PollingStatsComputation(
                new PollingHealthSummaryView(active, coolingDown, failing, disabled),
                providerCounts.entrySet().stream()
                        .map(entry -> new PollingBreakdownItemView(
                                entry.getKey().toLowerCase().replace(' ', '-'),
                                entry.getKey(),
                                entry.getValue()))
                        .sorted(Comparator.comparingLong(PollingBreakdownItemView::count).reversed()
                                .thenComparing(PollingBreakdownItemView::label))
                        .toList(),
                manualRuns,
                scheduledRuns,
                idleRuns,
                averagePollDurationMillis,
                errorPolls,
                duplicatePoints,
                errorPoints,
                manualRunPoints,
                scheduledRunPoints,
                idleRunPoints,
                fallbackSourcesWithErrors >= 0 ? fallbackSourcesWithErrors : derivedSourcesWithErrors);
    }

    private boolean isScheduledTrigger(SourcePollEvent event) {
        return "scheduler".equals(event.triggerName);
    }

    private boolean isIdleTrigger(SourcePollEvent event) {
        return "idle-source".equals(event.triggerName);
    }

    private boolean isManualTrigger(SourcePollEvent event) {
        return !isScheduledTrigger(event) && !isIdleTrigger(event);
    }

    private String providerLabel(ConfiguredSourceSnapshot source) {
        if (source.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2) {
            if (source.oauthProvider() == InboxBridgeConfig.OAuthProvider.GOOGLE) {
                return "Google";
            }
            if (source.oauthProvider() == InboxBridgeConfig.OAuthProvider.MICROSOFT) {
                return "Microsoft";
            }
        }
        String host = Optional.ofNullable(source.host()).orElse("").toLowerCase();
        if (host.contains("gmail") || host.contains("googlemail")) {
            return "Gmail";
        }
        if (host.contains("outlook") || host.contains("hotmail") || host.contains("live.") || host.contains("office365")) {
            return "Microsoft";
        }
        if (host.contains("yahoo")) {
            return "Yahoo";
        }
        if (host.contains("proton")) {
            return "Proton Mail";
        }
        if (source.protocol() == InboxBridgeConfig.Protocol.POP3) {
            return "Generic POP3";
        }
        return "Generic IMAP";
    }

    private record ConfiguredSourceSnapshot(
            String sourceId,
            boolean enabled,
            InboxBridgeConfig.Protocol protocol,
            InboxBridgeConfig.AuthMethod authMethod,
            InboxBridgeConfig.OAuthProvider oauthProvider,
            String host) {
    }

    private record TimedCount(Instant at, long count) {
    }

    private record PollingStatsComputation(
            PollingHealthSummaryView health,
            List<PollingBreakdownItemView> providerBreakdown,
            long manualRuns,
            long scheduledRuns,
            long idleRuns,
            long averagePollDurationMillis,
            long errorPolls,
            List<TimedCount> duplicatePoints,
            List<TimedCount> errorPoints,
            List<TimedCount> manualRunPoints,
            List<TimedCount> scheduledRunPoints,
            List<TimedCount> idleRunPoints,
            int sourcesWithErrors) {
    }

    private record ScopedStatsData(
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

}
