package dev.inboxbridge.service.polling;

import dev.inboxbridge.service.*;
import dev.inboxbridge.service.mail.MailSourceClient;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.dto.PollRunError;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PollingSourceExecutionService {

    private static final String GMAIL_ACCESS_REVOKED_MESSAGE =
            "The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Destination Mailbox.";
    private static final String MICROSOFT_ACCESS_REVOKED_MESSAGE =
            "The linked Microsoft account no longer grants InboxBridge access. Reconnect it from this mail account.";
    private static final String GOOGLE_SOURCE_ACCESS_REVOKED_MESSAGE =
            "The linked Google account no longer grants InboxBridge access. Reconnect it from this mail account.";
    private static final String STOPPED_BY_USER_MESSAGE = "Stopped by user.";

    private static final Logger LOG = Logger.getLogger(PollingSourceExecutionService.class);

    private final MailSourceClient mailSourceClient;
    private final ImportDeduplicationService importDeduplicationService;
    private final Instance<MailDestinationService> mailDestinationServices;
    private final SourcePollEventService sourcePollEventService;
    private final SourcePollingStateService sourcePollingStateService;
    private final PollThrottleService pollThrottleService;
    private final PollingLiveService pollingLiveService;
    private final PollCancellationService pollCancellationService;

    @Inject
    PollingSourceExecutionService(
            MailSourceClient mailSourceClient,
            ImportDeduplicationService importDeduplicationService,
            Instance<MailDestinationService> mailDestinationServices,
            SourcePollEventService sourcePollEventService,
            SourcePollingStateService sourcePollingStateService,
            PollThrottleService pollThrottleService,
            PollingLiveService pollingLiveService,
            PollCancellationService pollCancellationService) {
        this.mailSourceClient = mailSourceClient;
        this.importDeduplicationService = importDeduplicationService;
        this.mailDestinationServices = mailDestinationServices;
        this.sourcePollEventService = sourcePollEventService;
        this.sourcePollingStateService = sourcePollingStateService;
        this.pollThrottleService = pollThrottleService;
        this.pollingLiveService = pollingLiveService;
        this.pollCancellationService = pollCancellationService;
    }

    SourceExecutionOutcome execute(
            RuntimeEmailAccount emailAccount,
            String trigger,
            PollingSettingsService.EffectivePollingSettings settings,
            String liveRunId,
            String actorUsername,
            String executionSurface) {
        Instant startedAt = Instant.now();
        int fetched = 0;
        int imported = 0;
        long importedBytes = 0L;
        int duplicates = 0;
        int spamJunkMessageCount = 0;
        List<String> spamJunkFolderSummaries = new ArrayList<>();
        PollRunError errorDetail = null;
        String error = null;
        boolean shouldRecordFailure = false;
        boolean stoppedByUser = false;
        PollThrottleService.ThrottleLease sourceLease = PollThrottleService.ThrottleLease.noopLease();
        long sourceThrottleWaitMillis = 0L;
        long destinationThrottleWaitMillis = 0L;
        PollThrottleService.ThrottleAudit sourceThrottleAudit = null;
        PollThrottleService.ThrottleAudit destinationThrottleAudit = null;
        SourcePollingStateService.CooldownDecision cooldownDecision = null;

        try (PollCancellationService.Scope ignored = pollCancellationService == null
                ? PollCancellationService.Scope.noop()
                : pollCancellationService.bind(liveRunId, emailAccount.id())) {
            MailDestinationService destinationService = destinationService(emailAccount.destination());
            if (!destinationService.isLinked(emailAccount.destination())) {
                error = "Source " + emailAccount.id() + " cannot run because "
                        + destinationService.notLinkedMessage(emailAccount.destination());
                errorDetail = new PollRunError("gmail_account_not_linked", emailAccount.id(), error, null);
                return new SourceExecutionOutcome(
                        fetched,
                        imported,
                        importedBytes,
                        duplicates,
                        spamJunkMessageCount,
                        List.copyOf(spamJunkFolderSummaries),
                        errorDetail);
            }
            sourceLease = acquireSourceThrottle(emailAccount);
            sourceThrottleWaitMillis = Math.max(0L, sourceLease.waitedFor().toMillis());
            if (!awaitLiveCheckpoint(liveRunId)) {
                stoppedByUser = true;
                return new SourceExecutionOutcome(
                        fetched,
                        imported,
                        importedBytes,
                        duplicates,
                        spamJunkMessageCount,
                        List.copyOf(spamJunkFolderSummaries),
                        null);
            }
            try {
                Optional<MailSourceClient.MailboxCountProbe> spamProbe = mailSourceClient.probeSpamOrJunkFolder(emailAccount)
                        .filter(probe -> probe.messageCount() > 0);
                if (spamProbe.isPresent()) {
                    spamJunkMessageCount = spamProbe.get().messageCount();
                    spamJunkFolderSummaries.add(emailAccount.id() + " -> "
                            + spamProbe.get().folderName() + " (" + spamProbe.get().messageCount() + ")");
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
                fetched++;
                processedBytes += message.rawMessage().length;
                if (importDeduplicationService.alreadyImported(message, emailAccount.destination())) {
                    mailSourceClient.applyPostPollSettings(emailAccount, message);
                    recordSourceCheckpoint(emailAccount, message);
                    duplicates++;
                    publishLiveProgress(liveRunId, emailAccount.id(), messages.size(), totalBytes, processedBytes, fetched, imported, duplicates);
                    continue;
                }
                PollThrottleService.ThrottleLease destinationLease = acquireDestinationThrottle(emailAccount.destination());
                destinationThrottleWaitMillis += Math.max(0L, destinationLease.waitedFor().toMillis());
                try {
                    MailImportResponse importResponse = destinationService.importMessage(emailAccount.destination(), emailAccount, message);
                    importDeduplicationService.recordImport(message, emailAccount.destination(), importResponse);
                    mailSourceClient.applyPostPollSettings(emailAccount, message);
                    recordSourceCheckpoint(emailAccount, message);
                    destinationThrottleAudit = recordDestinationThrottleSuccess(emailAccount.destination());
                    imported++;
                    importedBytes += message.rawMessage().length;
                    publishLiveProgress(liveRunId, emailAccount.id(), messages.size(), totalBytes, processedBytes, fetched, imported, duplicates);
                } catch (RuntimeException destinationError) {
                    destinationThrottleAudit = recordDestinationThrottleFailure(emailAccount.destination(), destinationError);
                    throw destinationError;
                } finally {
                    releaseThrottle(destinationLease);
                }
            }
        } catch (RuntimeException e) {
            if (stoppedBecauseRunStopped(liveRunId, e)) {
                stoppedByUser = true;
            } else {
                error = "Source " + emailAccount.id() + " failed: "
                        + Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
                shouldRecordFailure = true;
                sourceThrottleAudit = recordSourceThrottleFailure(emailAccount, error);
                LOG.error(error, e);
                errorDetail = mapRuntimeError(emailAccount, error, e);
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
                        STOPPED_BY_USER_MESSAGE,
                        new PollDecisionSnapshot(
                                null,
                                null,
                                null,
                                sourceThrottleWaitMillis > 0L ? sourceThrottleWaitMillis : null,
                                sourceThrottleAudit == null ? null : sourceThrottleAudit.multiplierAfter(),
                                sourceThrottleAudit == null ? null : sourceThrottleAudit.nextAllowedAt(),
                                destinationThrottleWaitMillis > 0L ? destinationThrottleWaitMillis : null,
                                destinationThrottleAudit == null ? null : destinationThrottleAudit.multiplierAfter(),
                                destinationThrottleAudit == null ? null : destinationThrottleAudit.nextAllowedAt()));
                if (liveRunId != null && pollingLiveService != null) {
                    pollingLiveService.markSourceStopped(liveRunId, emailAccount.id(), fetched, imported, duplicates);
                }
            } else {
                if (error == null) {
                    sourcePollingStateService.recordSuccess(emailAccount.id(), finishedAt, settings);
                    sourceThrottleAudit = recordSourceThrottleSuccess(emailAccount);
                } else if (shouldRecordFailure) {
                    cooldownDecision = sourcePollingStateService.recordFailure(emailAccount.id(), finishedAt, error);
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
                        error,
                        new PollDecisionSnapshot(
                                cooldownDecision == null ? null : cooldownDecision.failureCategory(),
                                cooldownDecision == null ? null : cooldownDecision.backoff().toMillis(),
                                cooldownDecision == null ? null : cooldownDecision.cooldownUntil(),
                                sourceThrottleWaitMillis > 0L ? sourceThrottleWaitMillis : null,
                                sourceThrottleAudit == null ? null : sourceThrottleAudit.multiplierAfter(),
                                sourceThrottleAudit == null ? null : sourceThrottleAudit.nextAllowedAt(),
                                destinationThrottleWaitMillis > 0L ? destinationThrottleWaitMillis : null,
                                destinationThrottleAudit == null ? null : destinationThrottleAudit.multiplierAfter(),
                                destinationThrottleAudit == null ? null : destinationThrottleAudit.nextAllowedAt()));
                if (liveRunId != null && pollingLiveService != null) {
                    pollingLiveService.markSourceFinished(liveRunId, emailAccount.id(), fetched, imported, duplicates, error);
                }
            }
        }

        return new SourceExecutionOutcome(
                fetched,
                imported,
                importedBytes,
                duplicates,
                spamJunkMessageCount,
                List.copyOf(spamJunkFolderSummaries),
                errorDetail);
    }

    private boolean awaitLiveCheckpoint(String liveRunId) {
        return liveRunId == null || pollingLiveService == null || pollingLiveService.awaitIfPausedOrStopped(liveRunId);
    }

    private void publishLiveProgress(
            String liveRunId,
            String sourceId,
            int totalMessages,
            long totalBytes,
            long processedBytes,
            int fetched,
            int imported,
            int duplicates) {
        if (liveRunId == null || pollingLiveService == null) {
            return;
        }
        pollingLiveService.updateSourceProgress(liveRunId, sourceId, totalMessages, totalBytes, processedBytes, fetched, imported, duplicates);
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

    private MailDestinationService destinationService(MailDestinationTarget target) {
        for (MailDestinationService service : mailDestinationServices) {
            if (service.supports(target)) {
                return service;
            }
        }
        throw new IllegalStateException("No mail destination service is available for provider " + target.providerId());
    }

    private void recordSourceCheckpoint(RuntimeEmailAccount emailAccount, FetchedMessage message) {
        if (sourcePollingStateService == null) {
            return;
        }
        if (emailAccount.protocol() == InboxBridgeConfig.Protocol.IMAP) {
            sourcePollingStateService.recordImapCheckpoint(
                    emailAccount.id(),
                    DestinationIdentityKeys.forTarget(emailAccount.destination()),
                    message.folderName().orElse(emailAccount.folder().orElse("INBOX")),
                    message.uidValidity(),
                    message.uid(),
                    Instant.now());
            return;
        }
        if (emailAccount.protocol() == InboxBridgeConfig.Protocol.POP3) {
            sourcePollingStateService.recordPopCheckpoint(
                    emailAccount.id(),
                    DestinationIdentityKeys.forTarget(emailAccount.destination()),
                    message.popUidl(),
                    Instant.now());
        }
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
            String error,
            PollDecisionSnapshot decisionSnapshot) {
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
                    error,
                    decisionSnapshot == null ? PollDecisionSnapshot.empty() : decisionSnapshot);
        } catch (RuntimeException recordError) {
            LOG.warnf(recordError,
                    "Unable to record source poll event for %s; continuing without persisted last-run history",
                    sourceId);
        }
    }

    private PollThrottleService.ThrottleAudit recordSourceThrottleSuccess(RuntimeEmailAccount emailAccount) {
        if (pollThrottleService == null) {
            return null;
        }
        return pollThrottleService.recordSourceSuccess(emailAccount);
    }

    private PollThrottleService.ThrottleAudit recordSourceThrottleFailure(RuntimeEmailAccount emailAccount, String error) {
        if (pollThrottleService == null) {
            return null;
        }
        return pollThrottleService.recordSourceFailure(emailAccount, error);
    }

    private PollThrottleService.ThrottleAudit recordDestinationThrottleSuccess(MailDestinationTarget target) {
        if (pollThrottleService == null) {
            return null;
        }
        return pollThrottleService.recordDestinationSuccess(target);
    }

    private PollThrottleService.ThrottleAudit recordDestinationThrottleFailure(MailDestinationTarget target, RuntimeException error) {
        if (pollThrottleService == null) {
            return null;
        }
        return pollThrottleService.recordDestinationFailure(
                target,
                Optional.ofNullable(error.getMessage()).orElse(error.getClass().getSimpleName()));
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

    record SourceExecutionOutcome(
            int fetched,
            int imported,
            long importedBytes,
            int duplicates,
            int spamJunkMessageCount,
            List<String> spamJunkFolderSummaries,
            PollRunError error) {
    }
}
