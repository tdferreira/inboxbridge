package dev.inboxbridge.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;

import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.dto.GmailImportResponse;
import dev.inboxbridge.dto.PollRunResult;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PollingService {

    private static final Logger LOG = Logger.getLogger(PollingService.class);

    @Inject
    MailSourceClient mailSourceClient;

    @Inject
    ImportDeduplicationService importDeduplicationService;

    @Inject
    GmailImportService gmailImportService;

    @Inject
    GmailLabelService gmailLabelService;

    @Inject
    SourcePollEventService sourcePollEventService;

    @Inject
    RuntimeBridgeService runtimeBridgeService;

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    UserPollingSettingsService userPollingSettingsService;

    @Inject
    SourcePollingSettingsService sourcePollingSettingsService;

    @Inject
    SourcePollingStateService sourcePollingStateService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ActivePoll> activePoll = new AtomicReference<>();

    @Scheduled(every = "5s")
    void scheduledPoll() {
        runPoll("scheduler");
    }

    public PollRunResult runPoll(String trigger) {
        if (!running.compareAndSet(false, true)) {
            PollRunResult busy = new PollRunResult();
            busy.addError(currentBusyMessage());
            busy.finish();
            return busy;
        }

        PollRunResult result = new PollRunResult();
        try {
            activePoll.set(new ActivePoll(trigger, null, result.getStartedAt()));
            if (!"scheduler".equals(trigger)) {
                LOG.infof("Starting poll triggered by %s", trigger);
            }
            Instant now = Instant.now();
            boolean ignoreInterval = !"scheduler".equals(trigger);
            for (RuntimeBridge bridge : runtimeBridgeService.listEnabledForPolling()) {
                PollingSettingsService.EffectivePollingSettings settings = effectiveSettingsFor(bridge);
                SourcePollingStateService.PollEligibility eligibility = sourcePollingStateService.eligibility(
                        bridge.id(),
                        settings,
                        now,
                        ignoreInterval);
                if (!eligibility.shouldPoll()) {
                    if (ignoreInterval) {
                        result.addError(skipMessage(bridge, eligibility));
                    }
                    continue;
                }
                pollSource(bridge, trigger, settings, result);
            }
            return result;
        } catch (RuntimeException e) {
            LOG.error("Unexpected polling failure", e);
            result.addError(Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
            return result;
        } finally {
            result.finish();
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

    public PollRunResult runPollForSource(RuntimeBridge bridge, String trigger) {
        if (!running.compareAndSet(false, true)) {
            PollRunResult busy = new PollRunResult();
            busy.addError(currentBusyMessage());
            busy.finish();
            return busy;
        }

        PollRunResult result = new PollRunResult();
        try {
            activePoll.set(new ActivePoll(trigger, bridge.id(), result.getStartedAt()));
            LOG.infof("Starting single-source poll for %s triggered by %s", bridge.id(), trigger);
            PollingSettingsService.EffectivePollingSettings settings = effectiveSettingsFor(bridge);
            SourcePollingStateService.PollEligibility eligibility = sourcePollingStateService.eligibility(
                    bridge.id(),
                    settings,
                    Instant.now(),
                    true);
            if (!bridge.enabled()) {
                result.addError("Source " + bridge.id() + " is disabled.");
                return result;
            }
            if (!eligibility.shouldPoll()) {
                result.addError(skipMessage(bridge, eligibility));
                return result;
            }
            pollSource(bridge, trigger, settings, result);
            return result;
        } catch (RuntimeException e) {
            LOG.error("Unexpected single-source polling failure", e);
            result.addError(Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
            return result;
        } finally {
            result.finish();
            activePoll.set(null);
            running.set(false);
            LOG.infof("Single-source poll finished for %s: fetched=%d imported=%d duplicates=%d errors=%d",
                    bridge.id(), result.getFetched(), result.getImported(), result.getDuplicates(), result.getErrors().size());
        }
    }

    private void pollSource(
            RuntimeBridge bridge,
            String trigger,
            PollingSettingsService.EffectivePollingSettings settings,
            PollRunResult result) {
        Instant startedAt = Instant.now();
        int fetched = 0;
        int imported = 0;
        int duplicates = 0;
        String error = null;
        try {
            if (!runtimeBridgeService.gmailAccountLinked(bridge)) {
                throw new IllegalStateException(
                        "The Gmail account is not linked for this destination. Connect it from My Gmail Account before polling this source.");
            }
            try {
                mailSourceClient.probeSpamOrJunkFolder(bridge)
                        .filter(probe -> probe.messageCount() > 0)
                        .ifPresent(probe -> result.addSpamJunkFolderSummary(bridge.id(), probe.folderName(), probe.messageCount()));
            } catch (RuntimeException spamProbeError) {
                LOG.debugf(spamProbeError, "Unable to inspect spam/junk mailbox for source %s", bridge.id());
            }
            List<FetchedMessage> messages = mailSourceClient.fetch(bridge, settings.fetchWindow());
            List<String> labelIds = gmailLabelService.resolveLabelIds(bridge.gmailTarget(), bridge.customLabel());
            for (FetchedMessage message : messages) {
                result.incrementFetched();
                fetched++;
                if (importDeduplicationService.alreadyImported(message, bridge.gmailTarget())) {
                    result.incrementDuplicate();
                    duplicates++;
                    continue;
                }
                GmailImportResponse gmailResponse = gmailImportService.importMessage(bridge.gmailTarget(), message.rawMessage(), labelIds);
                importDeduplicationService.recordImport(message, bridge.gmailTarget(), gmailResponse);
                result.incrementImported();
                imported++;
            }
        } catch (RuntimeException e) {
            error = "Source " + bridge.id() + " failed: " + Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
            LOG.error(error, e);
            result.addError(error);
        } finally {
            Instant finishedAt = Instant.now();
            if (error == null) {
                sourcePollingStateService.recordSuccess(bridge.id(), finishedAt, settings);
            } else {
                sourcePollingStateService.recordFailure(bridge.id(), finishedAt, error);
            }
            sourcePollEventService.record(
                    bridge.id(),
                    trigger,
                    startedAt,
                    finishedAt,
                    fetched,
                    imported,
                    duplicates,
                    error);
        }
    }

    private PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeBridge bridge) {
        return sourcePollingSettingsService.effectiveSettingsFor(bridge);
    }

    private String skipMessage(RuntimeBridge bridge, SourcePollingStateService.PollEligibility eligibility) {
        return switch (eligibility.reason()) {
            case "DISABLED" -> "Source " + bridge.id() + " is skipped because polling is disabled for this fetcher.";
            case "COOLDOWN" -> "Source " + bridge.id() + " is cooling down until "
                    + Optional.ofNullable(eligibility.state())
                            .map(state -> String.valueOf(state.cooldownUntil()))
                            .orElse("a later retry time") + ".";
            case "INTERVAL" -> "Source " + bridge.id() + " is waiting for its next poll window at "
                    + Optional.ofNullable(eligibility.state())
                            .map(state -> String.valueOf(state.nextPollAt()))
                            .orElse("a later retry time") + ".";
            default -> "Source " + bridge.id() + " is not ready to poll yet.";
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

    private record ActivePoll(String trigger, String sourceId, Instant startedAt) {
    }
}
