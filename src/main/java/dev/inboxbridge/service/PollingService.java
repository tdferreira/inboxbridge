package dev.inboxbridge.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import dev.inboxbridge.config.BridgeConfig;
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
    BridgeConfig config;

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

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(every = "{bridge.poll-interval}")
    void scheduledPoll() {
        if (!config.pollEnabled()) {
            return;
        }
        runPoll("scheduler");
    }

    public PollRunResult runPoll(String trigger) {
        if (!running.compareAndSet(false, true)) {
            PollRunResult busy = new PollRunResult();
            busy.addError("A poll is already running");
            busy.finish();
            return busy;
        }

        PollRunResult result = new PollRunResult();
        try {
            LOG.infof("Starting poll triggered by %s", trigger);
            for (RuntimeBridge bridge : runtimeBridgeService.listEnabledForPolling()) {
                pollSource(bridge, trigger, result);
            }
            return result;
        } catch (RuntimeException e) {
            LOG.error("Unexpected polling failure", e);
            result.addError(Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName()));
            return result;
        } finally {
            result.finish();
            running.set(false);
            LOG.infof("Poll finished: fetched=%d imported=%d duplicates=%d errors=%d",
                    result.getFetched(), result.getImported(), result.getDuplicates(), result.getErrors().size());
        }
    }

    private void pollSource(RuntimeBridge bridge, String trigger, PollRunResult result) {
        Instant startedAt = Instant.now();
        int fetched = 0;
        int imported = 0;
        int duplicates = 0;
        String error = null;
        try {
            List<FetchedMessage> messages = mailSourceClient.fetch(bridge);
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
            sourcePollEventService.record(
                    bridge.id(),
                    trigger,
                    startedAt,
                    Instant.now(),
                    fetched,
                    imported,
                    duplicates,
                    error);
        }
    }
}
