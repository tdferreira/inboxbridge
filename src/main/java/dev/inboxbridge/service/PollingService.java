package dev.inboxbridge.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;

import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.dto.PollRunError;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.persistence.AppUser;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class PollingService {

        private static final String GMAIL_ACCESS_REVOKED_MESSAGE =
            "The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Destination Mailbox.";
    private static final String MICROSOFT_ACCESS_REVOKED_MESSAGE =
            "The linked Microsoft account no longer grants InboxBridge access. Reconnect it from this mail account.";
    private static final String GOOGLE_SOURCE_ACCESS_REVOKED_MESSAGE =
            "The linked Google account no longer grants InboxBridge access. Reconnect it from this mail account.";

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

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ActivePoll> activePoll = new AtomicReference<>();

    @Scheduled(every = "5s")
    void scheduledPoll() {
        runPoll("scheduler");
    }

    public PollRunResult runPoll(String trigger) {
        return runPollInternal(trigger, runtimeEmailAccountService.listEnabledForPolling(), null, false, false, false, null);
    }

    public PollRunResult runPollForAllUsers(AppUser actor, String trigger) {
        return runPollInternal(
                trigger,
                runtimeEmailAccountService.listEnabledForPolling(),
                actorRateLimitKey(actor),
                false,
                false,
                false,
                null);
    }

    public PollRunResult runPollForUser(AppUser actor, String trigger) {
        return runPollInternal(
                trigger,
                runtimeEmailAccountService.listEnabledForUser(actor),
                actorRateLimitKey(actor),
                false,
                false,
                false,
                null);
    }

    public PollRunResult runPollForSource(RuntimeEmailAccount emailAccount, String trigger) {
        return runPollForSource(emailAccount, trigger, null);
    }

    public PollRunResult runPollForSource(RuntimeEmailAccount emailAccount, String trigger, String actorKey) {
        return runPollInternal(trigger, List.of(emailAccount), actorKey, true, true, true, emailAccount.id());
    }

    private PollRunResult runPollInternal(
            String trigger,
            List<RuntimeEmailAccount> emailAccounts,
            String manualActorKey,
            boolean ignoreInterval,
            boolean ignoreCooldown,
            boolean singleSource,
            String singleSourceId) {
        if (!running.compareAndSet(false, true)) {
            PollRunResult busy = new PollRunResult();
            busy.addError(new PollRunError("poll_busy", singleSourceId, currentBusyMessage(), null));
            busy.finish();
            return busy;
        }

        PollRunResult result = new PollRunResult();
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
            activePoll.set(new ActivePoll(trigger, singleSourceId, result.getStartedAt()));
            if (!"scheduler".equals(trigger)) {
                LOG.infof("Starting %s poll triggered by %s", singleSource ? "single-source" : "multi-source", trigger);
            }
            Instant now = Instant.now();
            for (RuntimeEmailAccount emailAccount : emailAccounts) {
                PollingSettingsService.EffectivePollingSettings settings = effectiveSettingsFor(emailAccount);
                SourcePollingStateService.PollEligibility eligibility = sourcePollingStateService.eligibility(
                        emailAccount.id(),
                        settings,
                        now,
                        ignoreInterval,
                        ignoreCooldown);
                if (!eligibility.shouldPoll()) {
                    if (!"scheduler".equals(trigger)) {
                        result.addError(skipError(emailAccount, eligibility));
                    }
                    continue;
                }
                pollSource(emailAccount, trigger, settings, result);
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

    private void pollSource(
            RuntimeEmailAccount emailAccount,
            String trigger,
            PollingSettingsService.EffectivePollingSettings settings,
            PollRunResult result) {
        Instant startedAt = Instant.now();
        int fetched = 0;
        int imported = 0;
        int duplicates = 0;
        String error = null;
        boolean shouldRecordFailure = false;
        try {
            MailDestinationService destinationService = destinationService(emailAccount.destination());
            if (!destinationService.isLinked(emailAccount.destination())) {
                error = "Source " + emailAccount.id() + " cannot run because " + destinationService.notLinkedMessage(emailAccount.destination());
                result.addError(new PollRunError("gmail_account_not_linked", emailAccount.id(), error, null));
                return;
            }
            try {
                mailSourceClient.probeSpamOrJunkFolder(emailAccount)
                        .filter(probe -> probe.messageCount() > 0)
                        .ifPresent(probe -> result.addSpamJunkFolderSummary(emailAccount.id(), probe.folderName(), probe.messageCount()));
            } catch (RuntimeException spamProbeError) {
                LOG.debugf(spamProbeError, "Unable to inspect spam/junk mailbox for source %s", emailAccount.id());
            }
            List<FetchedMessage> messages = mailSourceClient.fetch(emailAccount, settings.fetchWindow());
            for (FetchedMessage message : messages) {
                result.incrementFetched();
                fetched++;
                if (importDeduplicationService.alreadyImported(message, emailAccount.destination())) {
                    result.incrementDuplicate();
                    duplicates++;
                    continue;
                }
                MailImportResponse importResponse = destinationService.importMessage(emailAccount.destination(), emailAccount, message);
                importDeduplicationService.recordImport(message, emailAccount.destination(), importResponse);
                result.incrementImported();
                imported++;
            }
        } catch (RuntimeException e) {
            error = "Source " + emailAccount.id() + " failed: " + Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
            shouldRecordFailure = true;
            LOG.error(error, e);
            result.addError(mapRuntimeError(emailAccount, error, e));
        } finally {
            Instant finishedAt = Instant.now();
            if (error == null) {
                sourcePollingStateService.recordSuccess(emailAccount.id(), finishedAt, settings);
            } else if (shouldRecordFailure) {
                sourcePollingStateService.recordFailure(emailAccount.id(), finishedAt, error);
            }
            sourcePollEventService.record(
                    emailAccount.id(),
                    trigger,
                    startedAt,
                    finishedAt,
                    fetched,
                    imported,
                    duplicates,
                    error);
        }
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
}
