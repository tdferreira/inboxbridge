package dev.inboxbridge.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
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
    private static final int TRIMESTER_BUCKETS = 4;
    private static final int SEMESTER_BUCKETS = 2;
    private static final int YEARLY_BUCKETS = 5;

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
                emailAccount.bridgeId,
                emailAccount.enabled,
                emailAccount.protocol,
                emailAccount.authMethod,
                emailAccount.oauthProvider,
                emailAccount.host));
        }
        Instant earliest = earliestTimelineInstant();
        Map<String, List<ImportTimelinePointView>> timelines = buildTimelinesFromInstants(
                importedMessageRepository.listImportedAtSince(earliest));
        List<SourcePollEvent> events = sourcePollEventService.listSince(earliest);
        PollingStatsComputation computation = computeEventStats(sources, events, sourcesWithErrors);
        return new GlobalPollingStatsView(
                importedMessageRepository.count(),
                sources.size(),
                (int) sources.stream().filter(ConfiguredSourceSnapshot::enabled).count(),
                computation.sourcesWithErrors(),
                computation.errorPolls(),
                buildTimeline(importedMessageRepository.summarizeByImportedDay(), DEFAULT_TIMELINE_DAYS),
                timelines,
                buildTimelinesFromTimedCounts(computation.duplicatePoints()),
                buildTimelinesFromTimedCounts(computation.errorPoints()),
                buildTimelinesFromTimedCounts(computation.manualRunPoints()),
                buildTimelinesFromTimedCounts(computation.scheduledRunPoints()),
                computation.health(),
                computation.providerBreakdown(),
                computation.manualRuns(),
                computation.scheduledRuns(),
                computation.averagePollDurationMillis());
    }

    public UserPollingStatsView userStats(Long userId) {
        List<UserEmailAccount> emailAccounts = userEmailAccountRepository.listByUserId(userId);
        List<ConfiguredSourceSnapshot> sources = emailAccounts.stream()
            .map(emailAccount -> new ConfiguredSourceSnapshot(
                emailAccount.bridgeId,
                emailAccount.enabled,
                emailAccount.protocol,
                emailAccount.authMethod,
                emailAccount.oauthProvider,
                emailAccount.host))
                .toList();
        String destinationKey = "user-gmail:" + userId;
        Instant earliest = earliestTimelineInstant();
        Map<String, List<ImportTimelinePointView>> timelines = buildTimelinesFromInstants(
                importedMessageRepository.listImportedAtSinceForDestinationKey(destinationKey, earliest));
        List<SourcePollEvent> events = sourcePollEventService.listBySourceIdsSince(
                sources.stream().map(ConfiguredSourceSnapshot::sourceId).toList(),
                earliest);
        ScopedStatsData scopedStats = buildScopedStats(
                sources,
                importedMessageRepository.countByDestinationKey(destinationKey),
                buildTimeline(importedMessageRepository.summarizeByImportedDayForDestinationKey(destinationKey), DEFAULT_TIMELINE_DAYS),
                timelines,
                events,
                -1);
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
                scopedStats.health(),
                scopedStats.providerBreakdown(),
                scopedStats.manualRuns(),
                scopedStats.scheduledRuns(),
                scopedStats.averagePollDurationMillis());
    }

        public SourcePollingStatsView sourceStats(RuntimeEmailAccount emailAccount) {
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
        List<ImportTimelinePointView> importsByDay = destinationKey == null
                ? buildTimeline(List.of(), DEFAULT_TIMELINE_DAYS)
                : buildTimeline(
                importedMessageRepository.summarizeByImportedDayForDestinationKeyAndSourceAccountId(destinationKey, emailAccount.id()),
                        DEFAULT_TIMELINE_DAYS);
        Map<String, List<ImportTimelinePointView>> timelines = destinationKey == null
                ? buildTimelinesFromInstants(List.of())
                : buildTimelinesFromInstants(
                importedMessageRepository.listImportedAtSinceForDestinationKeyAndSourceAccountId(destinationKey, emailAccount.id(), earliest));
        List<SourcePollEvent> events = sourcePollEventService.listBySourceIdsSince(List.of(emailAccount.id()), earliest);
        ScopedStatsData scopedStats = buildScopedStats(
                List.of(source),
                totalImportedMessages,
                importsByDay,
                timelines,
                events,
                -1);
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
                scopedStats.health(),
                scopedStats.providerBreakdown(),
                scopedStats.manualRuns(),
                scopedStats.scheduledRuns(),
                scopedStats.averagePollDurationMillis());
    }

    public PollingTimelineBundleView globalTimelineBundle(Instant fromInclusive, Instant toExclusive) {
        Instant normalizedTo = normalizeUpperBound(toExclusive);
        List<Instant> imports = importedMessageRepository.listImportedAtSince(fromInclusive).stream()
                .filter(value -> value.isBefore(normalizedTo))
                .toList();
        List<SourcePollEvent> events = sourcePollEventService.listSince(fromInclusive).stream()
                .filter(event -> event.finishedAt.isBefore(normalizedTo))
                .toList();
        return buildTimelineBundle(imports, events, fromInclusive, normalizedTo);
    }

    public PollingTimelineBundleView userTimelineBundle(Long userId, Instant fromInclusive, Instant toExclusive) {
        Instant normalizedTo = normalizeUpperBound(toExclusive);
        String destinationKey = userMailDestinationConfigRepository.findByUserId(userId)
            .map(config -> UserMailDestinationConfigService.PROVIDER_GMAIL.equals(config.provider) ? "user-gmail:" + userId : "user-destination:" + userId)
            .orElse("user-gmail:" + userId);
        List<Instant> imports = importedMessageRepository.listImportedAtSinceForDestinationKey(destinationKey, fromInclusive).stream()
                .filter(value -> value.isBefore(normalizedTo))
                .toList();
        List<String> sourceIds = userEmailAccountRepository.listByUserId(userId).stream()
            .map(emailAccount -> emailAccount.bridgeId)
                .toList();
        List<SourcePollEvent> events = sourcePollEventService.listBySourceIdsSince(sourceIds, fromInclusive).stream()
                .filter(event -> event.finishedAt.isBefore(normalizedTo))
                .toList();
        return buildTimelineBundle(imports, events, fromInclusive, normalizedTo);
    }

        public PollingTimelineBundleView sourceTimelineBundle(RuntimeEmailAccount emailAccount, Instant fromInclusive, Instant toExclusive) {
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
        return buildTimelineBundle(imports, events, fromInclusive, normalizedTo);
    }

    private ScopedStatsData buildScopedStats(
            List<ConfiguredSourceSnapshot> sources,
            long totalImportedMessages,
            List<ImportTimelinePointView> importsByDay,
            Map<String, List<ImportTimelinePointView>> importTimelines,
            List<SourcePollEvent> events,
            int fallbackSourcesWithErrors) {
        PollingStatsComputation computation = computeEventStats(sources, events, fallbackSourcesWithErrors);
        return new ScopedStatsData(
                totalImportedMessages,
                sources.size(),
                (int) sources.stream().filter(ConfiguredSourceSnapshot::enabled).count(),
                computation.sourcesWithErrors(),
                computation.errorPolls(),
                importsByDay,
                importTimelines,
                buildTimelinesFromTimedCounts(computation.duplicatePoints()),
                buildTimelinesFromTimedCounts(computation.errorPoints()),
                buildTimelinesFromTimedCounts(computation.manualRunPoints()),
                buildTimelinesFromTimedCounts(computation.scheduledRunPoints()),
                computation.health(),
                computation.providerBreakdown(),
                computation.manualRuns(),
                computation.scheduledRuns(),
                computation.averagePollDurationMillis());
    }

    private PollingTimelineBundleView buildTimelineBundle(
            List<Instant> imports,
            List<SourcePollEvent> events,
            Instant fromInclusive,
            Instant toExclusive) {
        return new PollingTimelineBundleView(
                Map.of("custom", buildCustomTimeline(
                        imports.stream().map(value -> new TimedCount(value, 1L)).toList(),
                        fromInclusive,
                        toExclusive)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(event -> event.duplicateCount > 0)
                                .map(event -> new TimedCount(event.finishedAt, event.duplicateCount))
                                .toList(),
                        fromInclusive,
                        toExclusive)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(event -> "ERROR".equals(event.status))
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(event -> !"scheduler".equals(event.triggerName))
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive)),
                Map.of("custom", buildCustomTimeline(
                        events.stream()
                                .filter(event -> "scheduler".equals(event.triggerName))
                                .map(event -> new TimedCount(event.finishedAt, 1L))
                                .toList(),
                        fromInclusive,
                        toExclusive)));
    }

    private Instant normalizeUpperBound(Instant value) {
        return value == null ? Instant.now() : value;
    }

    private Instant earliestTimelineInstant() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .withMonth(Month.JANUARY.getValue())
                .withDayOfMonth(1)
                .minusYears(YEARLY_BUCKETS - 1L)
                .toInstant(ZoneOffset.UTC);
    }

    private Map<String, List<ImportTimelinePointView>> buildTimelinesFromInstants(List<Instant> importedAtValues) {
        List<TimedCount> timedCounts = importedAtValues.stream()
                .map(value -> new TimedCount(value, 1L))
                .toList();
        return buildTimelinesFromTimedCounts(timedCounts);
    }

    private Map<String, List<ImportTimelinePointView>> buildTimelinesFromTimedCounts(List<TimedCount> values) {
        Map<String, List<ImportTimelinePointView>> timelines = new LinkedHashMap<>();
        timelines.put("today", buildTodayTimeline(values));
        timelines.put("yesterday", buildYesterdayTimeline(values));
        timelines.put("pastWeek", buildPastWeekTimeline(values));
        timelines.put("pastMonth", buildPastMonthTimeline(values));
        timelines.put("pastTrimester", buildPastTrimesterTimeline(values));
        timelines.put("pastSemester", buildPastSemesterTimeline(values));
        timelines.put("pastYear", buildPastYearTimeline(values));
        return timelines;
    }

    private List<ImportTimelinePointView> buildTodayTimeline(List<TimedCount> values) {
        return buildHourlyRangeTimeline(values, LocalDate.now(ZoneOffset.UTC));
    }

    private List<ImportTimelinePointView> buildYesterdayTimeline(List<TimedCount> values) {
        return buildHourlyRangeTimeline(values, LocalDate.now(ZoneOffset.UTC).minusDays(1));
    }

    private List<ImportTimelinePointView> buildHourlyRangeTimeline(List<TimedCount> values, LocalDate day) {
        Map<Integer, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDateTime timestamp = value.at().atZone(ZoneOffset.UTC).toLocalDateTime();
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

    private List<ImportTimelinePointView> buildPastWeekTimeline(List<TimedCount> values) {
        return buildRecentDailyTimeline(values, 7);
    }

    private List<ImportTimelinePointView> buildPastMonthTimeline(List<TimedCount> values) {
        return buildRecentDailyTimeline(values, 30);
    }

    private List<ImportTimelinePointView> buildRecentDailyTimeline(List<TimedCount> values, int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(days - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(ZoneOffset.UTC).toLocalDate();
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

    private List<ImportTimelinePointView> buildPastTrimesterTimeline(List<TimedCount> values) {
        return buildRecentMonthlyTimeline(values, 3);
    }

    private List<ImportTimelinePointView> buildPastSemesterTimeline(List<TimedCount> values) {
        return buildRecentMonthlyTimeline(values, 6);
    }

    private List<ImportTimelinePointView> buildPastYearTimeline(List<TimedCount> values) {
        return buildRecentMonthlyTimeline(values, 12);
    }

    private List<ImportTimelinePointView> buildCustomTimeline(
            List<TimedCount> values,
            Instant fromInclusive,
            Instant toExclusive) {
        long hours = Math.max(1L, ChronoUnit.HOURS.between(fromInclusive, toExclusive));
        long days = Math.max(1L, ChronoUnit.DAYS.between(fromInclusive, toExclusive));
        if (hours <= 72L) {
            return buildHourlyCustomTimeline(values, fromInclusive, toExclusive);
        }
        if (days <= 180L) {
            return buildDailyCustomTimeline(values, fromInclusive, toExclusive);
        }
        return buildMonthlyCustomTimeline(values, fromInclusive, toExclusive);
    }

    private List<ImportTimelinePointView> buildHourlyCustomTimeline(
            List<TimedCount> values,
            Instant fromInclusive,
            Instant toExclusive) {
        LocalDateTime start = fromInclusive.atZone(ZoneOffset.UTC).toLocalDateTime()
                .truncatedTo(ChronoUnit.HOURS);
        LocalDateTime end = toExclusive.atZone(ZoneOffset.UTC).toLocalDateTime()
                .truncatedTo(ChronoUnit.HOURS);
        Map<LocalDateTime, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDateTime bucket = value.at().atZone(ZoneOffset.UTC).toLocalDateTime().truncatedTo(ChronoUnit.HOURS);
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
            Instant toExclusive) {
        LocalDate start = fromInclusive.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = toExclusive.minusMillis(1).atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(ZoneOffset.UTC).toLocalDate();
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
            Instant toExclusive) {
        LocalDate start = fromInclusive.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        LocalDate end = toExclusive.minusMillis(1).atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
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

    private List<ImportTimelinePointView> buildRecentMonthlyTimeline(List<TimedCount> values, int months) {
        LocalDate currentMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        LocalDate start = currentMonth.minusMonths(months - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
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

    private List<ImportTimelinePointView> buildHourlyTimeline(List<TimedCount> values) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        Map<LocalDateTime, Long> counts = new HashMap<>();
        LocalDateTime start = now.minusHours(HOURLY_BUCKETS - 1L);
        for (TimedCount value : values) {
            LocalDateTime bucket = value.at().atZone(ZoneOffset.UTC).toLocalDateTime()
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, value.count(), Long::sum);
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

    private List<ImportTimelinePointView> buildDailyTimeline(List<TimedCount> values) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(DAILY_BUCKETS - 1L);
        Map<LocalDate, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(ZoneOffset.UTC).toLocalDate();
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, value.count(), Long::sum);
            }
        }
        List<ImportTimelinePointView> points = new ArrayList<>();
        for (int offset = 0; offset < DAILY_BUCKETS; offset++) {
            LocalDate bucket = start.plusDays(offset);
            points.add(new ImportTimelinePointView(bucket.toString(), counts.getOrDefault(bucket, 0L)));
        }
        return points;
    }

    private List<ImportTimelinePointView> buildMonthlyTimeline(List<TimedCount> values) {
        LocalDate monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        Map<LocalDate, Long> counts = new HashMap<>();
        LocalDate start = monthStart.minusMonths(MONTHLY_BUCKETS - 1L);
        for (TimedCount value : values) {
            LocalDate bucket = value.at().atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, value.count(), Long::sum);
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

    private List<ImportTimelinePointView> buildQuarterTimeline(List<TimedCount> values) {
        QuarterBucket current = QuarterBucket.from(LocalDate.now(ZoneOffset.UTC));
        QuarterBucket start = current.minusQuarters(TRIMESTER_BUCKETS - 1);
        Map<QuarterBucket, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            QuarterBucket bucket = QuarterBucket.from(value.at().atZone(ZoneOffset.UTC).toLocalDate());
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, value.count(), Long::sum);
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

    private List<ImportTimelinePointView> buildSemesterTimeline(List<TimedCount> values) {
        SemesterBucket current = SemesterBucket.from(LocalDate.now(ZoneOffset.UTC));
        SemesterBucket start = current.minusSemesters(SEMESTER_BUCKETS - 1);
        Map<SemesterBucket, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            SemesterBucket bucket = SemesterBucket.from(value.at().atZone(ZoneOffset.UTC).toLocalDate());
            if (!bucket.isBefore(start)) {
                counts.merge(bucket, value.count(), Long::sum);
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

    private List<ImportTimelinePointView> buildYearTimeline(List<TimedCount> values) {
        int currentYear = LocalDate.now(ZoneOffset.UTC).getYear();
        int startYear = currentYear - YEARLY_BUCKETS + 1;
        Map<Integer, Long> counts = new HashMap<>();
        for (TimedCount value : values) {
            int bucket = value.at().atZone(ZoneOffset.UTC).getYear();
            if (bucket >= startYear) {
                counts.merge(bucket, value.count(), Long::sum);
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
                .filter(event -> !"scheduler".equals(event.triggerName))
                .count();
        long scheduledRuns = events.size() - manualRuns;
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
                .filter(event -> !"scheduler".equals(event.triggerName))
                .map(event -> new TimedCount(event.finishedAt, 1L))
                .toList();
        List<TimedCount> scheduledRunPoints = events.stream()
                .filter(event -> "scheduler".equals(event.triggerName))
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
                averagePollDurationMillis,
                errorPolls,
                duplicatePoints,
                errorPoints,
                manualRunPoints,
                scheduledRunPoints,
                fallbackSourcesWithErrors >= 0 ? fallbackSourcesWithErrors : derivedSourcesWithErrors);
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
            long averagePollDurationMillis,
            long errorPolls,
            List<TimedCount> duplicatePoints,
            List<TimedCount> errorPoints,
            List<TimedCount> manualRunPoints,
            List<TimedCount> scheduledRunPoints,
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
            PollingHealthSummaryView health,
            List<PollingBreakdownItemView> providerBreakdown,
            long manualRuns,
            long scheduledRuns,
            long averagePollDurationMillis) {
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
