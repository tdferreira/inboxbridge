package dev.inboxbridge.service.polling;

import dev.inboxbridge.service.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

    @Inject
    PollingTimelineService pollingTimelineService;

    public PollingStatsService() {
    }

    public PollingStatsService(
            ImportedMessageRepository importedMessageRepository,
            UserEmailAccountRepository userEmailAccountRepository,
            UserMailDestinationConfigRepository userMailDestinationConfigRepository,
            SourcePollEventService sourcePollEventService,
            EnvSourceService envSourceService,
            SourcePollingStateService sourcePollingStateService,
            PollingTimelineService pollingTimelineService) {
        this.importedMessageRepository = importedMessageRepository;
        this.userEmailAccountRepository = userEmailAccountRepository;
        this.userMailDestinationConfigRepository = userMailDestinationConfigRepository;
        this.sourcePollEventService = sourcePollEventService;
        this.envSourceService = envSourceService;
        this.sourcePollingStateService = sourcePollingStateService;
        this.pollingTimelineService = pollingTimelineService;
    }

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
        Map<String, List<ImportTimelinePointView>> timelines = timelineService().buildTimelinesFromInstants(imports, effectiveZoneId);
        List<SourcePollEvent> events = sourcePollEventService.listSince(earliest);
        PollingStatsComputation computation = computeEventStats(sources, events, sourcesWithErrors);
        return new GlobalPollingStatsView(
                importedMessageRepository.count(),
                sources.size(),
                (int) sources.stream().filter(ConfiguredSourceSnapshot::enabled).count(),
                computation.sourcesWithErrors(),
                computation.errorPolls(),
                timelineService().buildRecentDailyTimeline(timelineService().toTimedCounts(imports), DEFAULT_TIMELINE_DAYS, effectiveZoneId),
                timelines,
                timelineService().buildTimelinesFromTimedCounts(computation.duplicatePoints(), effectiveZoneId),
                timelineService().buildTimelinesFromTimedCounts(computation.errorPoints(), effectiveZoneId),
                timelineService().buildTimelinesFromTimedCounts(computation.manualRunPoints(), effectiveZoneId),
                timelineService().buildTimelinesFromTimedCounts(computation.scheduledRunPoints(), effectiveZoneId),
                timelineService().buildTimelinesFromTimedCounts(computation.idleRunPoints(), effectiveZoneId),
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
        Map<String, List<ImportTimelinePointView>> timelines = timelineService().buildTimelinesFromInstants(imports, effectiveZoneId);
        List<SourcePollEvent> events = sourcePollEventService.listBySourceIdsSince(
                sources.stream().map(ConfiguredSourceSnapshot::sourceId).toList(),
                earliest);
        ScopedStatsData scopedStats = buildScopedStats(
                sources,
                importedMessageRepository.countByDestinationKey(destinationKey),
                timelineService().buildRecentDailyTimeline(timelineService().toTimedCounts(imports), DEFAULT_TIMELINE_DAYS, effectiveZoneId),
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
        Map<String, List<ImportTimelinePointView>> timelines = destinationKey == null
                ? timelineService().buildTimelinesFromInstants(List.of(), effectiveZoneId)
                : timelineService().buildTimelinesFromInstants(imports, effectiveZoneId);
        List<SourcePollEvent> events = sourcePollEventService.listBySourceIdsSince(List.of(emailAccount.id()), earliest);
        ScopedStatsData scopedStats = buildScopedStats(
                List.of(source),
                totalImportedMessages,
                timelineService().buildRecentDailyTimeline(timelineService().toTimedCounts(imports), DEFAULT_TIMELINE_DAYS, effectiveZoneId),
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
        return timelineService().buildTimelineBundle(imports, events, fromInclusive, normalizedTo, effectiveZoneId(zoneId));
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
        return timelineService().buildTimelineBundle(imports, events, fromInclusive, normalizedTo, effectiveZoneId(zoneId));
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
        return timelineService().buildTimelineBundle(imports, events, fromInclusive, normalizedTo, effectiveZoneId(zoneId));
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
                timelineService().buildTimelinesFromTimedCounts(computation.duplicatePoints(), zoneId),
                timelineService().buildTimelinesFromTimedCounts(computation.errorPoints(), zoneId),
                timelineService().buildTimelinesFromTimedCounts(computation.manualRunPoints(), zoneId),
                timelineService().buildTimelinesFromTimedCounts(computation.scheduledRunPoints(), zoneId),
                timelineService().buildTimelinesFromTimedCounts(computation.idleRunPoints(), zoneId),
                computation.health(),
                computation.providerBreakdown(),
                computation.manualRuns(),
                computation.scheduledRuns(),
                computation.idleRuns(),
                computation.averagePollDurationMillis());
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
        List<PollingTimelineService.TimedCount> duplicatePoints = events.stream()
                .filter(event -> event.duplicateCount > 0)
                .map(event -> new PollingTimelineService.TimedCount(event.finishedAt, event.duplicateCount))
                .toList();
        List<PollingTimelineService.TimedCount> errorPoints = events.stream()
                .filter(event -> "ERROR".equals(event.status))
                .map(event -> new PollingTimelineService.TimedCount(event.finishedAt, 1L))
                .toList();
        List<PollingTimelineService.TimedCount> manualRunPoints = events.stream()
                .filter(this::isManualTrigger)
                .map(event -> new PollingTimelineService.TimedCount(event.finishedAt, 1L))
                .toList();
        List<PollingTimelineService.TimedCount> scheduledRunPoints = events.stream()
                .filter(this::isScheduledTrigger)
                .map(event -> new PollingTimelineService.TimedCount(event.finishedAt, 1L))
                .toList();
        List<PollingTimelineService.TimedCount> idleRunPoints = events.stream()
                .filter(this::isIdleTrigger)
                .map(event -> new PollingTimelineService.TimedCount(event.finishedAt, 1L))
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

    private record PollingStatsComputation(
            PollingHealthSummaryView health,
            List<PollingBreakdownItemView> providerBreakdown,
            long manualRuns,
            long scheduledRuns,
            long idleRuns,
            long averagePollDurationMillis,
            long errorPolls,
            List<PollingTimelineService.TimedCount> duplicatePoints,
            List<PollingTimelineService.TimedCount> errorPoints,
            List<PollingTimelineService.TimedCount> manualRunPoints,
            List<PollingTimelineService.TimedCount> scheduledRunPoints,
            List<PollingTimelineService.TimedCount> idleRunPoints,
            int sourcesWithErrors) {
    }

    private PollingTimelineService timelineService() {
        return pollingTimelineService == null ? new PollingTimelineService() : pollingTimelineService;
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
