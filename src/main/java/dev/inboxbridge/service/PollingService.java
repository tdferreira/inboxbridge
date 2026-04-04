package dev.inboxbridge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.jboss.logging.Logger;

import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.MailImportResponse;
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

    private static final String GMAIL_ACCESS_REVOKED_MESSAGE =
            "The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Destination Mailbox.";
    private static final String MICROSOFT_ACCESS_REVOKED_MESSAGE =
            "The linked Microsoft account no longer grants InboxBridge access. Reconnect it from this mail account.";
    private static final String GOOGLE_SOURCE_ACCESS_REVOKED_MESSAGE =
            "The linked Google account no longer grants InboxBridge access. Reconnect it from this mail account.";
    private static final String STOPPED_BY_USER_MESSAGE = "Stopped by user.";

    private static final Logger LOG = Logger.getLogger(PollingService.class);

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

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ActivePoll> activePoll = new AtomicReference<>();

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
            PollRunResult busy = new PollRunResult();
            busy.addError(new PollRunError("poll_busy", singleSourceId, currentBusyMessage(), null));
            busy.finish();
            return busy;
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
        pollSource(emailAccount, trigger, settings, tally, liveRunId, actorUsername, executionSurface);
    }

    private void pollSource(
            RuntimeEmailAccount emailAccount,
            String trigger,
            PollingSettingsService.EffectivePollingSettings settings,
            SourcePollTally tally,
            String liveRunId,
            String actorUsername,
            String executionSurface) {
        Instant startedAt = Instant.now();
        int fetched = 0;
        int imported = 0;
        long importedBytes = 0L;
        int duplicates = 0;
        int spamJunkMessageCount = 0;
        String error = null;
        boolean shouldRecordFailure = false;
        boolean stoppedByUser = false;
        PollThrottleService.ThrottleLease sourceLease = PollThrottleService.ThrottleLease.noopLease();
        try (PollCancellationService.Scope ignored = pollCancellationService == null
                ? PollCancellationService.Scope.noop()
                : pollCancellationService.bind(liveRunId, emailAccount.id())) {
            MailDestinationService destinationService = destinationService(emailAccount.destination());
            if (!destinationService.isLinked(emailAccount.destination())) {
                error = "Source " + emailAccount.id() + " cannot run because " + destinationService.notLinkedMessage(emailAccount.destination());
                tally.addError(new PollRunError("gmail_account_not_linked", emailAccount.id(), error, null));
                return;
            }
            sourceLease = acquireSourceThrottle(emailAccount);
            if (!awaitLiveCheckpoint(liveRunId)) {
                stoppedByUser = true;
                return;
            }
            try {
                Optional<MailSourceClient.MailboxCountProbe> spamProbe = mailSourceClient.probeSpamOrJunkFolder(emailAccount)
                        .filter(probe -> probe.messageCount() > 0);
                if (spamProbe.isPresent()) {
                    spamJunkMessageCount = spamProbe.get().messageCount();
                    tally.addSpamJunkFolderSummary(emailAccount.id(), spamProbe.get().folderName(), spamProbe.get().messageCount());
                }
            } catch (RuntimeException spamProbeError) {
                LOG.debugf(spamProbeError, "Unable to inspect spam/junk mailbox for source %s", emailAccount.id());
            }
            List<FetchedMessage> messages = mailSourceClient.fetch(emailAccount, settings.fetchWindow());
            long totalBytes = totalBytes(messages);
            long processedBytes = 0L;
            publishLiveProgress(liveRunId, emailAccount.id(), messages.size(), totalBytes, processedBytes, fetched, imported, duplicates);
            for (FetchedMessage message : messages) {
                if (!awaitLiveCheckpoint(liveRunId)) {
                    stoppedByUser = true;
                    break;
                }
                tally.incrementFetched();
                fetched++;
                processedBytes += message.rawMessage().length;
                if (importDeduplicationService.alreadyImported(message, emailAccount.destination())) {
                    mailSourceClient.applyPostPollSettings(emailAccount, message);
                    tally.incrementDuplicate();
                    duplicates++;
                    publishLiveProgress(liveRunId, emailAccount.id(), messages.size(), totalBytes, processedBytes, fetched, imported, duplicates);
                    continue;
                }
                PollThrottleService.ThrottleLease destinationLease = acquireDestinationThrottle(emailAccount.destination());
                try {
                    MailImportResponse importResponse = destinationService.importMessage(emailAccount.destination(), emailAccount, message);
                    importDeduplicationService.recordImport(message, emailAccount.destination(), importResponse);
                    mailSourceClient.applyPostPollSettings(emailAccount, message);
                    recordDestinationThrottleSuccess(emailAccount.destination());
                    tally.incrementImported();
                    tally.addImportedBytes(message.rawMessage().length);
                    imported++;
                    importedBytes += message.rawMessage().length;
                    publishLiveProgress(liveRunId, emailAccount.id(), messages.size(), totalBytes, processedBytes, fetched, imported, duplicates);
                } catch (RuntimeException destinationError) {
                    recordDestinationThrottleFailure(emailAccount.destination(), destinationError);
                    throw destinationError;
                } finally {
                    releaseThrottle(destinationLease);
                }
            }
        } catch (RuntimeException e) {
            if (stoppedBecauseRunStopped(liveRunId, e)) {
                stoppedByUser = true;
            } else {
            error = "Source " + emailAccount.id() + " failed: " + Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
            shouldRecordFailure = true;
            recordSourceThrottleFailure(emailAccount, error);
            LOG.error(error, e);
            tally.addError(mapRuntimeError(emailAccount, error, e));
            }
        } finally {
            releaseThrottle(sourceLease);
            Instant finishedAt = Instant.now();
            if (stoppedByUser) {
                recordSourcePollEvent(
                        emailAccount.id(),
                        trigger,
                        startedAt,
                        finishedAt,
                        fetched,
                        imported,
                        importedBytes,
                        duplicates,
                        spamJunkMessageCount,
                        actorUsername,
                        executionSurface,
                        STOPPED_BY_USER_MESSAGE);
                if (liveRunId != null && pollingLiveService != null) {
                    pollingLiveService.markSourceStopped(liveRunId, emailAccount.id(), fetched, imported, duplicates);
                }
                return;
            }
            if (error == null) {
                sourcePollingStateService.recordSuccess(emailAccount.id(), finishedAt, settings);
                recordSourceThrottleSuccess(emailAccount);
            } else if (shouldRecordFailure) {
                sourcePollingStateService.recordFailure(emailAccount.id(), finishedAt, error);
            }
            recordSourcePollEvent(
                    emailAccount.id(),
                    trigger,
                    startedAt,
                    finishedAt,
                    fetched,
                    imported,
                    importedBytes,
                    duplicates,
                    spamJunkMessageCount,
                    actorUsername,
                    executionSurface,
                    error);
            if (liveRunId != null && pollingLiveService != null) {
                pollingLiveService.markSourceFinished(liveRunId, emailAccount.id(), fetched, imported, duplicates, error);
            }
        }
    }

    private boolean awaitLiveCheckpoint(String liveRunId) {
        return liveRunId == null || pollingLiveService == null || pollingLiveService.awaitIfPausedOrStopped(liveRunId);
    }

    private void publishLiveProgress(String liveRunId, String sourceId, int totalMessages, long totalBytes, long processedBytes, int fetched, int imported, int duplicates) {
        if (liveRunId == null || pollingLiveService == null) {
            return;
        }
        pollingLiveService.updateSourceProgress(liveRunId, sourceId, totalMessages, totalBytes, processedBytes, fetched, imported, duplicates);
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
                false).shouldPoll();
    }

    private long totalBytes(List<FetchedMessage> messages) {
        long total = 0L;
        for (FetchedMessage message : messages) {
            if (message != null && message.rawMessage() != null) {
                total += message.rawMessage().length;
            }
        }
        return total;
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

    private boolean stoppedBecauseRunStopped(String liveRunId, RuntimeException error) {
        if (isStopped(liveRunId)) {
            return true;
        }
        if (Thread.currentThread().isInterrupted()) {
            return true;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isStopped(String liveRunId) {
        return liveRunId != null && pollingLiveService != null && pollingLiveService.stopRequested(liveRunId);
    }

    private PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeEmailAccount emailAccount) {
        return sourcePollingSettingsService.effectiveSettingsFor(emailAccount);
    }

    private MailDestinationService destinationService(MailDestinationTarget target) {
        for (MailDestinationService service : mailDestinationServices) {
            if (service.supports(target)) {
                return service;
            }
        }
        throw new IllegalStateException("No mail destination service is available for provider " + target.providerId());
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
            case "user-ui", "app-fetcher" -> "MY_INBOXBRIDGE";
            case "admin-ui", "admin-fetcher" -> "ADMINISTRATION";
            case "remote-ui", "remote-admin", "remote-source" -> "INBOXBRIDGE_GO";
            default -> null;
        };
    }

    private PollThrottleService.ThrottleLease acquireSourceThrottle(RuntimeEmailAccount emailAccount) {
        if (pollThrottleService == null) {
            return PollThrottleService.ThrottleLease.noopLease();
        }
        return pollThrottleService.acquireSourceMailboxPermit(emailAccount);
    }

    private PollThrottleService.ThrottleLease acquireDestinationThrottle(MailDestinationTarget target) {
        if (pollThrottleService == null) {
            return PollThrottleService.ThrottleLease.noopLease();
        }
        return pollThrottleService.acquireDestinationDeliveryPermit(target);
    }

    private void releaseThrottle(PollThrottleService.ThrottleLease lease) {
        if (pollThrottleService == null) {
            return;
        }
        pollThrottleService.release(lease);
    }

    private void recordSourcePollEvent(
            String sourceId,
            String trigger,
            Instant startedAt,
            Instant finishedAt,
            int fetched,
            int imported,
            long importedBytes,
            int duplicates,
            int spamJunkMessageCount,
            String actorUsername,
            String executionSurface,
            String error) {
        if (sourcePollEventService == null) {
            return;
        }
        try {
            sourcePollEventService.record(
                    sourceId,
                    trigger,
                    startedAt,
                    finishedAt,
                    fetched,
                    imported,
                    importedBytes,
                    duplicates,
                    spamJunkMessageCount,
                    actorUsername,
                    executionSurface,
                    error);
        } catch (RuntimeException recordError) {
            LOG.warnf(recordError,
                    "Unable to record source poll event for %s; continuing without persisted last-run history",
                    sourceId);
        }
    }

    private void recordSourceThrottleSuccess(RuntimeEmailAccount emailAccount) {
        if (pollThrottleService == null) {
            return;
        }
        pollThrottleService.recordSourceSuccess(emailAccount);
    }

    private void recordSourceThrottleFailure(RuntimeEmailAccount emailAccount, String error) {
        if (pollThrottleService == null) {
            return;
        }
        pollThrottleService.recordSourceFailure(emailAccount, error);
    }

    private void recordDestinationThrottleSuccess(MailDestinationTarget target) {
        if (pollThrottleService == null) {
            return;
        }
        pollThrottleService.recordDestinationSuccess(target);
    }

    private void recordDestinationThrottleFailure(MailDestinationTarget target, RuntimeException error) {
        if (pollThrottleService == null) {
            return;
        }
        pollThrottleService.recordDestinationFailure(target,
                Optional.ofNullable(error.getMessage()).orElse(error.getClass().getSimpleName()));
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

    private PollRunError mapRuntimeError(RuntimeEmailAccount bridge, String formattedMessage, RuntimeException error) {
        String rawMessage = Optional.ofNullable(error.getMessage()).orElse("");
        if (GMAIL_ACCESS_REVOKED_MESSAGE.equals(rawMessage)) {
            return new PollRunError("gmail_access_revoked", bridge.id(), formattedMessage, null);
        }
        if (GmailApiMailDestinationService.GMAIL_ACCOUNT_NOT_LINKED_MESSAGE.equals(rawMessage)) {
            return new PollRunError("gmail_account_not_linked", bridge.id(), formattedMessage, null);
        }
        if (MICROSOFT_ACCESS_REVOKED_MESSAGE.equals(rawMessage)) {
            return new PollRunError("microsoft_access_revoked", bridge.id(), formattedMessage, null);
        }
        if (GOOGLE_SOURCE_ACCESS_REVOKED_MESSAGE.equals(rawMessage)) {
            return new PollRunError("google_source_access_revoked", bridge.id(), formattedMessage, null);
        }
        return new PollRunError("generic", bridge.id(), formattedMessage, null);
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

        private void incrementFetched() {
            fetched++;
        }

        private void incrementImported() {
            imported++;
        }

        private void incrementDuplicate() {
            duplicates++;
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
