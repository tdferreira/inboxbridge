package dev.inboxbridge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.jboss.logging.Logger;

import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.dto.PollRunError;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.PollStatusView;
import dev.inboxbridge.persistence.AppUser;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class PollingService {

    private static final int MAX_PARALLEL_SOURCE_POLLS = 8;

    private static final Logger LOG = Logger.getLogger(PollingService.class);

    @Inject
    PollingSourceExecutionService pollingSourceExecutionService;

    @Inject
    MailSourceClient mailSourceClient;

    @Inject
    ImportDeduplicationService importDeduplicationService;

    @Inject
    Instance<MailDestinationService> mailDestinationServices;

    @Inject
    SourcePollEventService sourcePollEventService;

    @Inject
    RuntimeEmailAccountService runtimeEmailAccountService;

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    UserPollingSettingsService userPollingSettingsService;

    @Inject
    SourcePollingSettingsService sourcePollingSettingsService;

    @Inject
    SourcePollingStateService sourcePollingStateService;

    @Inject
    ManualPollRateLimitService manualPollRateLimitService;

    @Inject
    PollThrottleService pollThrottleService;

    @Inject
    PollingLiveService pollingLiveService;

    @Inject
    PollCancellationService pollCancellationService;

    @Inject
    ImapIdleHealthService imapIdleHealthService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ActivePoll> activePoll = new AtomicReference<>();
    private final Set<String> activeIdleSources = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Scheduled(every = "5s")
    void scheduledPoll() {
        runPoll("scheduler");
    }

    public PollRunResult runPoll(String trigger) {
        return runPollInternal(trigger, runtimeEmailAccountService.listEnabledForPolling(), null, null, false, false, false, null, executionSurfaceForTrigger(trigger));
    }

    public PollRunResult runPollForAllUsers(AppUser actor, String trigger) {
        return runPollInternal(
                trigger,
                runtimeEmailAccountService.listEnabledForPolling(),
                actor,
                actorRateLimitKey(actor),
                true,
                true,
                false,
                null,
                executionSurfaceForTrigger(trigger));
    }

    public PollRunResult runPollForUser(AppUser actor, String trigger) {
        return runPollInternal(
                trigger,
                runtimeEmailAccountService.listEnabledForUser(actor),
                actor,
                actorRateLimitKey(actor),
                true,
                true,
                false,
                null,
                executionSurfaceForTrigger(trigger));
    }

    public PollRunResult runPollForSource(RuntimeEmailAccount emailAccount, String trigger) {
        return runPollForSource(emailAccount, trigger, null, null);
    }

    public PollRunResult runPollForSource(RuntimeEmailAccount emailAccount, String trigger, String actorKey) {
        return runPollForSource(emailAccount, trigger, null, actorKey);
    }

    public PollRunResult runPollForSource(RuntimeEmailAccount emailAccount, String trigger, AppUser actor, String actorKey) {
        return runPollInternal(trigger, List.of(emailAccount), actor, actorKey, true, true, true, emailAccount.id(), executionSurfaceForTrigger(trigger));
    }

    public PollRunResult runIdleTriggeredPollForSource(RuntimeEmailAccount emailAccount) {
        if (!activeIdleSources.add(emailAccount.id())) {
            return busyResult(emailAccount.id(), "A realtime source activation is already running for source " + emailAccount.id() + ".");
        }
        try {
            ActivePoll current = activePoll.get();
            if (running.get() && current != null && "scheduler".equals(current.trigger())) {
                return runConcurrentIdleSourcePoll(emailAccount);
            }
            return runPollInternal("idle-source", List.of(emailAccount), null, null, true, true, true, emailAccount.id(), executionSurfaceForTrigger("idle-source"));
        } finally {
            activeIdleSources.remove(emailAccount.id());
        }
    }

    public PollStatusView status() {
        ActivePoll current = activePoll.get();
        if (current == null) {
            return new PollStatusView(false, null, null, null);
        }
        return new PollStatusView(true, current.sourceId(), current.trigger(), current.startedAt());
    }

    private PollRunResult runPollInternal(
            String trigger,
            List<RuntimeEmailAccount> emailAccounts,
            AppUser actor,
            String manualActorKey,
            boolean ignoreInterval,
            boolean ignoreCooldown,
            boolean singleSource,
            String singleSourceId,
            String executionSurface) {
        if (!running.compareAndSet(false, true)) {
            return busyResult(singleSourceId, currentBusyMessage());
        }

        PollRunResult result = new PollRunResult();
        PollingLiveService.PollRunHandle liveRun = null;
        try {
            if (manualActorKey != null) {
                ManualPollRateLimitService.Decision decision = manualPollRateLimitService.tryAcquire(
                        manualActorKey,
                        pollingSettingsService.effectiveManualPollRateLimit().maxRuns(),
                        pollingSettingsService.effectiveManualPollRateLimit().window(),
                        Instant.now());
                if (!decision.allowed()) {
                    result.addError(new PollRunError(
                            "manual_poll_rate_limited",
                            singleSourceId,
                            "Manual polling is temporarily rate limited until "
                                    + Optional.ofNullable(decision.retryAt()).map(String::valueOf).orElse("a later retry time") + ".",
                            Optional.ofNullable(decision.retryAt()).map(String::valueOf).orElse(null)));
                    return result;
                }
            }
            if ("scheduler".equals(trigger)) {
                emailAccounts = schedulerEligibleEmailAccounts(emailAccounts, Instant.now());
                if (emailAccounts.isEmpty()) {
                    return result;
                }
            }
            if (pollingLiveService != null) {
                liveRun = pollingLiveService.startRun(trigger, emailAccounts, actorOrSystem(actor));
                if (liveRun != null && pollCancellationService != null) {
                    String liveRunId = liveRun.runId();
                    pollingLiveService.registerCancellationAction(liveRunId, () -> pollCancellationService.cancelRun(liveRunId));
                }
            }
            activePoll.set(new ActivePoll(trigger, singleSourceId, result.getStartedAt()));
            if (!"scheduler".equals(trigger)) {
                LOG.infof("Starting %s poll triggered by %s", singleSource ? "single-source" : "multi-source", trigger);
            }
            Instant now = Instant.now();
            if (liveRun != null) {
                runParallelLivePoll(trigger, now, result, liveRun.runId(), emailAccounts, ignoreInterval, ignoreCooldown, actorUsername(actor), executionSurface);
            } else {
                runParallelLocalPoll(trigger, now, result, emailAccounts, ignoreInterval, ignoreCooldown, actorUsername(actor), executionSurface);
            }
            return result;
        } catch (RuntimeException e) {
            LOG.error("Unexpected polling failure", e);
            result.addError(Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
            return result;
        } finally {
            result.finish();
            if (liveRun != null && pollingLiveService != null) {
                boolean stopped = pollingLiveService.stopRequested(liveRun.runId());
                if (stopped) {
                    result.markStopped(pollingLiveService.stopRequestedByUsername(liveRun.runId()));
                }
                pollingLiveService.finishRun(
                        liveRun.runId(),
                        stopped ? "STOPPED" : result.getErrors().isEmpty() ? "COMPLETED" : "FAILED");
            }
            if (liveRun != null && pollCancellationService != null) {
                pollCancellationService.clearRun(liveRun.runId());
            }
            activePoll.set(null);
            running.set(false);
            if (!"scheduler".equals(trigger)
                    || result.getFetched() > 0
                    || result.getImported() > 0
                    || result.getDuplicates() > 0
                    || !result.getErrors().isEmpty()) {
                LOG.infof("Poll finished: fetched=%d imported=%d duplicates=%d errors=%d",
                        result.getFetched(), result.getImported(), result.getDuplicates(), result.getErrors().size());
            }
        }
    }

    private void pollSingleSource(
            String trigger,
            Instant now,
            SourcePollTally tally,
            String liveRunId,
            RuntimeEmailAccount emailAccount,
            boolean ignoreInterval,
            boolean ignoreCooldown,
            String actorUsername,
            String executionSurface) {
        if (!emailAccount.enabled()) {
            if (!"scheduler".equals(trigger)) {
                tally.addError(new PollRunError(
                        "source_disabled",
                        emailAccount.id(),
                        "Source " + emailAccount.id() + " is disabled.",
                        null));
            }
            if (liveRunId != null && pollingLiveService != null) {
                pollingLiveService.markSourceFinished(liveRunId, emailAccount.id(), 0, 0, 0, "disabled");
            }
            return;
        }
        PollingSettingsService.EffectivePollingSettings settings = effectiveSettingsFor(emailAccount);
        SourcePollingStateService.PollEligibility eligibility = sourcePollingStateService.eligibility(
                emailAccount.id(),
                settings,
                now,
                ignoreInterval,
                ignoreCooldown);
        if (!eligibility.shouldPoll()) {
            if (!"scheduler".equals(trigger)) {
                tally.addError(skipError(emailAccount, eligibility));
            }
            if (liveRunId != null && pollingLiveService != null) {
                pollingLiveService.markSourceFinished(liveRunId, emailAccount.id(), 0, 0, 0, eligibility.reason());
            }
            return;
        }
        activePoll.set(new ActivePoll(trigger, emailAccount.id(), now));
        PollingSourceExecutionService.SourceExecutionOutcome outcome = sourceExecutionService().execute(
                emailAccount,
                trigger,
                settings,
                liveRunId,
                actorUsername,
                executionSurface);
        tally.addFetched(outcome.fetched());
        tally.addImported(outcome.imported());
        tally.addImportedBytes(outcome.importedBytes());
        tally.addDuplicates(outcome.duplicates());
        tally.addSpamJunkMessageCount(outcome.spamJunkMessageCount());
        outcome.spamJunkFolderSummaries().forEach(tally::addSpamJunkFolderSummary);
        tally.addError(outcome.error());
    }

    private List<RuntimeEmailAccount> schedulerEligibleEmailAccounts(List<RuntimeEmailAccount> emailAccounts, Instant now) {
        return emailAccounts.stream()
                .filter(RuntimeEmailAccount::enabled)
                .filter(isSchedulerEligible(now))
                .toList();
    }

    private Predicate<RuntimeEmailAccount> isSchedulerEligible(Instant now) {
        return (emailAccount) -> sourcePollingStateService.eligibility(
                emailAccount.id(),
                effectiveSettingsFor(emailAccount),
                now,
                false,
                false).shouldPoll() && isSchedulerAllowedForFetchMode(emailAccount, now);
    }

    private boolean isSchedulerAllowedForFetchMode(RuntimeEmailAccount emailAccount, Instant now) {
        if (emailAccount.fetchMode() != SourceFetchMode.IDLE) {
            return true;
        }
        return imapIdleHealthService != null
                && imapIdleHealthService.shouldSchedulerFallback(emailAccount.id(), now);
    }

    private void runParallelLivePoll(
            String trigger,
            Instant now,
            PollRunResult result,
            String liveRunId,
            List<RuntimeEmailAccount> emailAccounts,
            boolean ignoreInterval,
            boolean ignoreCooldown,
            String actorUsername,
            String executionSurface) {
        java.util.Map<String, RuntimeEmailAccount> emailAccountsById = emailAccounts.stream()
                .collect(java.util.stream.Collectors.toMap(RuntimeEmailAccount::id, emailAccount -> emailAccount));
        runParallelPoll(trigger, now, result, workerParallelism(emailAccounts.size()), liveRunId, () -> {
            String nextSourceId = pollingLiveService.nextSourceId(liveRunId);
            if (nextSourceId == null) {
                return null;
            }
            return emailAccountsById.get(nextSourceId);
        }, ignoreInterval, ignoreCooldown, actorUsername, executionSurface);
    }

    private void runParallelLocalPoll(
            String trigger,
            Instant now,
            PollRunResult result,
            List<RuntimeEmailAccount> emailAccounts,
            boolean ignoreInterval,
            boolean ignoreCooldown,
            String actorUsername,
            String executionSurface) {
        ConcurrentLinkedQueue<RuntimeEmailAccount> queue = new ConcurrentLinkedQueue<>(emailAccounts);
        runParallelPoll(trigger, now, result, workerParallelism(emailAccounts.size()), null, queue::poll, ignoreInterval, ignoreCooldown, actorUsername, executionSurface);
    }

    private void runParallelPoll(
            String trigger,
            Instant now,
            PollRunResult result,
            int parallelism,
            String liveRunId,
            SourceSupplier sourceSupplier,
            boolean ignoreInterval,
            boolean ignoreCooldown,
            String actorUsername,
            String executionSurface) {
        if (parallelism <= 1) {
            SourcePollTally tally = new SourcePollTally();
            while (true) {
                RuntimeEmailAccount emailAccount = sourceSupplier.next();
                if (emailAccount == null) {
                    break;
                }
                pollSingleSource(trigger, now, tally, liveRunId, emailAccount, ignoreInterval, ignoreCooldown, actorUsername, executionSurface);
            }
            tally.mergeInto(result);
            return;
        }

        List<Future<SourcePollTally>> futures = new ArrayList<>();
        try (ExecutorService executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            if (liveRunId != null && pollingLiveService != null) {
                pollingLiveService.registerCancellationAction(liveRunId, executor::shutdownNow);
            }
            for (int workerIndex = 0; workerIndex < parallelism; workerIndex++) {
                futures.add(executor.submit(() -> pollWorker(trigger, now, liveRunId, sourceSupplier, ignoreInterval, ignoreCooldown, actorUsername, executionSurface)));
            }
            for (Future<SourcePollTally> future : futures) {
                future.get().mergeInto(result);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (!isStopped(liveRunId)) {
                result.addError("Polling was interrupted unexpectedly.");
            }
        } catch (java.util.concurrent.ExecutionException executionError) {
            Throwable cause = executionError.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Polling worker failed unexpectedly", cause);
        }
    }

    private SourcePollTally pollWorker(
            String trigger,
            Instant now,
            String liveRunId,
            SourceSupplier sourceSupplier,
            boolean ignoreInterval,
            boolean ignoreCooldown,
            String actorUsername,
            String executionSurface) {
        SourcePollTally tally = new SourcePollTally();
        while (!Thread.currentThread().isInterrupted()) {
            RuntimeEmailAccount emailAccount = sourceSupplier.next();
            if (emailAccount == null) {
                break;
            }
            pollSingleSource(trigger, now, tally, liveRunId, emailAccount, ignoreInterval, ignoreCooldown, actorUsername, executionSurface);
        }
        return tally;
    }

    private int workerParallelism(int sourceCount) {
        return Math.max(1, Math.min(MAX_PARALLEL_SOURCE_POLLS, sourceCount));
    }

    private boolean isStopped(String liveRunId) {
        return liveRunId != null && pollingLiveService != null && pollingLiveService.stopRequested(liveRunId);
    }

    private PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeEmailAccount emailAccount) {
        return sourcePollingSettingsService.effectiveSettingsFor(emailAccount);
    }

    private String actorRateLimitKey(AppUser actor) {
        return actor == null || actor.id == null ? null : actor.role + ":" + actor.id;
    }

    private AppUser actorOrSystem(AppUser actor) {
        if (actor != null) {
            return actor;
        }
        AppUser system = new AppUser();
        system.id = -1L;
        system.username = "system";
        system.role = AppUser.Role.ADMIN;
        return system;
    }

    private String actorUsername(AppUser actor) {
        if (actor == null || actor.username == null || actor.username.isBlank()) {
            return null;
        }
        return actor.username;
    }

    private String executionSurfaceForTrigger(String trigger) {
        return switch (String.valueOf(trigger)) {
            case "scheduler" -> "AUTOMATIC";
            case "idle-source" -> "AUTOMATIC";
            case "user-ui", "app-fetcher" -> "MY_INBOXBRIDGE";
            case "admin-ui", "admin-fetcher" -> "ADMINISTRATION";
            case "remote-ui", "remote-admin", "remote-source" -> "INBOXBRIDGE_GO";
            default -> null;
        };
    }

    public boolean isRunning() {
        return running.get();
    }

    private PollRunResult runConcurrentIdleSourcePoll(RuntimeEmailAccount emailAccount) {
        PollRunResult result = new PollRunResult();
        try {
            LOG.infof("Starting single-source poll triggered by idle-source alongside the active scheduler run for %s", emailAccount.id());
            SourcePollTally tally = new SourcePollTally();
            pollSingleSource(
                    "idle-source",
                    Instant.now(),
                    tally,
                    null,
                    emailAccount,
                    true,
                    true,
                    null,
                    executionSurfaceForTrigger("idle-source"));
            tally.mergeInto(result);
            return result;
        } catch (RuntimeException e) {
            LOG.error("Unexpected concurrent idle-source polling failure", e);
            result.addError(Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
            return result;
        } finally {
            result.finish();
            LOG.infof("Poll finished: fetched=%d imported=%d duplicates=%d errors=%d",
                    result.getFetched(), result.getImported(), result.getDuplicates(), result.getErrors().size());
        }
    }

    private PollRunError skipError(RuntimeEmailAccount bridge, SourcePollingStateService.PollEligibility eligibility) {
        return switch (eligibility.reason()) {
            case "DISABLED" -> new PollRunError(
                    "source_polling_disabled",
                    bridge.id(),
                    "Source " + bridge.id() + " is skipped because polling is disabled for this fetcher.",
                    null);
            case "COOLDOWN" -> new PollRunError(
                    "source_cooling_down",
                    bridge.id(),
                    "Source " + bridge.id() + " is cooling down until "
                            + Optional.ofNullable(eligibility.state())
                                    .map(state -> String.valueOf(state.cooldownUntil()))
                                    .orElse("a later retry time") + ".",
                    Optional.ofNullable(eligibility.state())
                            .map(state -> String.valueOf(state.cooldownUntil()))
                            .orElse(null));
            case "INTERVAL" -> new PollRunError(
                    "source_waiting_next_window",
                    bridge.id(),
                    "Source " + bridge.id() + " is waiting for its next poll window at "
                            + Optional.ofNullable(eligibility.state())
                                    .map(state -> String.valueOf(state.nextPollAt()))
                                    .orElse("a later retry time") + ".",
                    Optional.ofNullable(eligibility.state())
                            .map(state -> String.valueOf(state.nextPollAt()))
                            .orElse(null));
            default -> new PollRunError(
                    "source_not_ready",
                    bridge.id(),
                    "Source " + bridge.id() + " is not ready to poll yet.",
                    null);
        };
    }

    private String currentBusyMessage() {
        ActivePoll current = activePoll.get();
        if (current == null) {
            return "A poll is already running";
        }
        if (current.sourceId() != null) {
            return "A poll is already running for source " + current.sourceId()
                    + " (trigger=" + current.trigger()
                    + ", startedAt=" + current.startedAt() + ")";
        }
        return "A poll is already running"
                + " (trigger=" + current.trigger()
                + ", startedAt=" + current.startedAt() + ")";
    }

    private PollRunResult busyResult(String singleSourceId, String message) {
        PollRunResult busy = new PollRunResult();
        busy.addError(new PollRunError("poll_busy", singleSourceId, message, null));
        busy.finish();
        return busy;
    }

    private PollingSourceExecutionService sourceExecutionService() {
        if (pollingSourceExecutionService != null) {
            return pollingSourceExecutionService;
        }
        return new PollingSourceExecutionService(
                mailSourceClient,
                importDeduplicationService,
                mailDestinationServices,
                sourcePollEventService,
                sourcePollingStateService,
                pollThrottleService,
                pollingLiveService,
                pollCancellationService);
    }

    private record ActivePoll(String trigger, String sourceId, Instant startedAt) {
    }

    @FunctionalInterface
    private interface SourceSupplier {
        RuntimeEmailAccount next();
    }

    private static final class SourcePollTally {
        private int fetched;
        private int imported;
        private long importedBytes;
        private int duplicates;
        private int spamJunkMessageCount;
        private final List<String> spamJunkFolderSummaries = new ArrayList<>();
        private final List<PollRunError> errors = new ArrayList<>();

        private void addFetched(int count) {
            fetched += Math.max(0, count);
        }

        private void addImported(int count) {
            imported += Math.max(0, count);
        }

        private void addDuplicates(int count) {
            duplicates += Math.max(0, count);
        }

        private void addImportedBytes(long bytes) {
            importedBytes += Math.max(0L, bytes);
        }

        private void addError(PollRunError error) {
            if (error != null) {
                errors.add(error);
            }
        }

        private void addSpamJunkFolderSummary(String sourceId, String folderName, int messageCount) {
            spamJunkMessageCount += Math.max(0, messageCount);
            spamJunkFolderSummaries.add(sourceId + " -> " + folderName + " (" + messageCount + ")");
        }

        private void addSpamJunkFolderSummary(String summary) {
            if (summary == null || summary.isBlank()) {
                return;
            }
            spamJunkFolderSummaries.add(summary);
        }

        private void addSpamJunkMessageCount(int messageCount) {
            spamJunkMessageCount += Math.max(0, messageCount);
        }

        private void mergeInto(PollRunResult result) {
            result.addFetched(fetched);
            result.addImported(imported);
            result.addImportedBytes(importedBytes);
            result.addDuplicates(duplicates);
            result.addSpamJunkMessageCount(spamJunkMessageCount);
            spamJunkFolderSummaries.forEach(result::addSpamJunkFolderSummary);
            errors.forEach(result::addError);
        }
    }
}
