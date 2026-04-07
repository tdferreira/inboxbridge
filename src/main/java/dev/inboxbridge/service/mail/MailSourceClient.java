package dev.inboxbridge.service.mail;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.service.polling.PollingSettingsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class MailSourceClient {

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    MailSourcePostPollActionService mailSourcePostPollActionService;

    @Inject
    MailSourceConnectionProbeService mailSourceConnectionProbeService;

    @Inject
    MailSourceFetchService mailSourceFetchService;

    public MailSourceClient() {
    }

    public MailSourceClient(
            PollingSettingsService pollingSettingsService,
            MailSourcePostPollActionService mailSourcePostPollActionService,
            MailSourceConnectionProbeService mailSourceConnectionProbeService,
            MailSourceFetchService mailSourceFetchService) {
        this.pollingSettingsService = pollingSettingsService;
        this.mailSourcePostPollActionService = mailSourcePostPollActionService;
        this.mailSourceConnectionProbeService = mailSourceConnectionProbeService;
        this.mailSourceFetchService = mailSourceFetchService;
    }

    public List<FetchedMessage> fetch(InboxBridgeConfig.Source source) {
        return fetch(source, pollingSettingsService.effectiveSettings().fetchWindow());
    }

    public List<FetchedMessage> fetch(InboxBridgeConfig.Source source, int fetchWindow) {
        return mailSourceFetchService.fetch(source, fetchWindow);
    }

    public List<FetchedMessage> fetch(RuntimeEmailAccount bridge) {
        return fetch(bridge, pollingSettingsService.effectiveSettings().fetchWindow());
    }

    public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
        return mailSourceFetchService.fetch(bridge, fetchWindow);
    }

    public EmailAccountConnectionTestResult testConnection(RuntimeEmailAccount bridge) {
        return mailSourceConnectionProbeService.testConnection(bridge);
    }

    public List<String> listFolders(RuntimeEmailAccount bridge) {
        return mailSourceConnectionProbeService.listFolders(bridge);
    }

    public Optional<MailboxCountProbe> probeSpamOrJunkFolder(RuntimeEmailAccount bridge) {
        return mailSourceConnectionProbeService.probeSpamOrJunkFolder(bridge);
    }

    public void applyPostPollSettings(RuntimeEmailAccount bridge, FetchedMessage message) {
        mailSourcePostPollActionService.apply(bridge, message);
    }

    public static boolean isRetryableMicrosoftOAuthFailure(Throwable error) {
        return MailFailureClassifier.classify(error).retryableOAuthSessionFailure();
    }

    public record MailboxCountProbe(String folderName, int messageCount) {
    }
}
