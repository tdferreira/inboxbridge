package dev.connexa.inboxbridge.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import dev.connexa.inboxbridge.config.BridgeConfig;
import dev.connexa.inboxbridge.domain.FetchedMessage;
import dev.connexa.inboxbridge.dto.GmailImportResponse;
import dev.connexa.inboxbridge.dto.PollRunResult;
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
            for (BridgeConfig.Source source : config.sources()) {
                if (!source.enabled()) {
                    continue;
                }
                pollSource(source, result);
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

    private void pollSource(BridgeConfig.Source source, PollRunResult result) {
        try {
            List<FetchedMessage> messages = mailSourceClient.fetch(source);
            List<String> labelIds = gmailLabelService.resolveLabelIds(source.customLabel());
            for (FetchedMessage message : messages) {
                result.incrementFetched();
                if (importDeduplicationService.alreadyImported(message)) {
                    result.incrementDuplicate();
                    continue;
                }
                GmailImportResponse gmailResponse = gmailImportService.importMessage(message.rawMessage(), labelIds);
                importDeduplicationService.recordImport(message, gmailResponse);
                result.incrementImported();
            }
        } catch (RuntimeException e) {
            String error = "Source " + source.id() + " failed: " + Optional.ofNullable(e.getMessage()).orElse(e.getClass().getSimpleName());
            LOG.error(error, e);
            result.addError(error);
        }
    }
}
