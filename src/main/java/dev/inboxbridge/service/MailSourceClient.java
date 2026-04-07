package dev.inboxbridge.service;

import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MailSourceClient {

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    SourcePollingStateService sourcePollingStateService;

    @Inject
    MimeHashService mimeHashService;

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    PollCancellationService pollCancellationService;

    @Inject
    MailSessionFactory mailSessionFactory;

    @Inject
    MailSourceFolderService mailSourceFolderService;

    @Inject
    MailSourceMessageMapper mailSourceMessageMapper;

    @Inject
    MailSourceCheckpointSelector mailSourceCheckpointSelector;

    @Inject
    MailSourceConnectionService mailSourceConnectionService;

    @Inject
    MailSourcePostPollActionService mailSourcePostPollActionService;

    @Inject
    MailSourceConnectionProbeService mailSourceConnectionProbeService;

    @Inject
    MailSourceFetchService mailSourceFetchService;

    public List<FetchedMessage> fetch(InboxBridgeConfig.Source source) {
        return fetch(source, pollingSettingsService.effectiveSettings().fetchWindow());
    }

    public List<FetchedMessage> fetch(InboxBridgeConfig.Source source, int fetchWindow) {
        return mailSourceFetchService().fetch(source, fetchWindow);
    }

    public List<FetchedMessage> fetch(RuntimeEmailAccount bridge) {
        return fetch(bridge, pollingSettingsService.effectiveSettings().fetchWindow());
    }

    public List<FetchedMessage> fetch(RuntimeEmailAccount bridge, int fetchWindow) {
        return mailSourceFetchService().fetch(bridge, fetchWindow);
    }

    public EmailAccountConnectionTestResult testConnection(RuntimeEmailAccount bridge) {
        return mailSourceConnectionProbeService().testConnection(bridge);
    }

    public List<String> listFolders(RuntimeEmailAccount bridge) {
        return mailSourceConnectionProbeService().listFolders(bridge);
    }

    public Optional<MailboxCountProbe> probeSpamOrJunkFolder(RuntimeEmailAccount bridge) {
        return mailSourceConnectionProbeService().probeSpamOrJunkFolder(bridge);
    }

    public void applyPostPollSettings(RuntimeEmailAccount bridge, FetchedMessage message) {
        mailSourcePostPollActionService().apply(bridge, message);
    }

    static boolean isRetryableMicrosoftOAuthFailure(Throwable error) {
        return MailFailureClassifier.classify(error).retryableOAuthSessionFailure();
    }

    private MailSessionFactory mailSessionFactory() {
        if (mailSessionFactory != null) {
            return mailSessionFactory;
        }
        MailSessionFactory fallback = new MailSessionFactory();
        fallback.mailClientConfig = new dev.inboxbridge.config.MailClientConfig() {
            @Override
            public java.time.Duration connectionTimeout() {
                return java.time.Duration.ofSeconds(20);
            }

            @Override
            public java.time.Duration operationTimeout() {
                return java.time.Duration.ofSeconds(20);
            }

            @Override
            public java.time.Duration idleOperationTimeout() {
                return java.time.Duration.ZERO;
            }
        };
        mailSessionFactory = fallback;
        return mailSessionFactory;
    }

    private MailSourceFolderService mailSourceFolderService() {
        if (mailSourceFolderService == null) {
            mailSourceFolderService = new MailSourceFolderService();
        }
        return mailSourceFolderService;
    }

    private MailSourceMessageMapper mailSourceMessageMapper() {
        if (mailSourceMessageMapper != null) {
            return mailSourceMessageMapper;
        }
        MailSourceMessageMapper fallback = new MailSourceMessageMapper();
        fallback.mimeHashService = mimeHashService == null ? new MimeHashService() : mimeHashService;
        mailSourceMessageMapper = fallback;
        return mailSourceMessageMapper;
    }

    private MailSourceCheckpointSelector mailSourceCheckpointSelector() {
        if (mailSourceCheckpointSelector == null) {
            mailSourceCheckpointSelector = new MailSourceCheckpointSelector();
        }
        return mailSourceCheckpointSelector;
    }

    private MailSourceConnectionService mailSourceConnectionService() {
        if (mailSourceConnectionService != null) {
            return mailSourceConnectionService;
        }
        MailSourceConnectionService fallback = new MailSourceConnectionService();
        fallback.microsoftOAuthService = microsoftOAuthService;
        fallback.googleOAuthService = googleOAuthService;
        mailSourceConnectionService = fallback;
        return mailSourceConnectionService;
    }

    private MailSourcePostPollActionService mailSourcePostPollActionService() {
        if (mailSourcePostPollActionService != null) {
            return mailSourcePostPollActionService;
        }
        MailSourcePostPollActionService fallback = new MailSourcePostPollActionService();
        fallback.mailSessionFactory = mailSessionFactory();
        fallback.mailSourceConnectionService = mailSourceConnectionService();
        fallback.mailSourceMessageMapper = mailSourceMessageMapper();
        fallback.pollCancellationService = pollCancellationService;
        mailSourcePostPollActionService = fallback;
        return mailSourcePostPollActionService;
    }

    private MailSourceConnectionProbeService mailSourceConnectionProbeService() {
        if (mailSourceConnectionProbeService != null) {
            return mailSourceConnectionProbeService;
        }
        MailSourceConnectionProbeService fallback = new MailSourceConnectionProbeService();
        fallback.mailSessionFactory = mailSessionFactory();
        fallback.mailSourceConnectionService = mailSourceConnectionService();
        fallback.mailSourceFolderService = mailSourceFolderService();
        fallback.mailSourceMessageMapper = mailSourceMessageMapper();
        fallback.pollCancellationService = pollCancellationService;
        mailSourceConnectionProbeService = fallback;
        return mailSourceConnectionProbeService;
    }

    private MailSourceFetchService mailSourceFetchService() {
        if (mailSourceFetchService != null) {
            return mailSourceFetchService;
        }
        MailSourceFetchService fallback = new MailSourceFetchService();
        fallback.mailSessionFactory = mailSessionFactory();
        fallback.mailSourceConnectionService = mailSourceConnectionService();
        fallback.mailSourceCheckpointSelector = mailSourceCheckpointSelector();
        fallback.mailSourceMessageMapper = mailSourceMessageMapper();
        fallback.sourcePollingStateService = sourcePollingStateService;
        fallback.pollCancellationService = pollCancellationService;
        mailSourceFetchService = fallback;
        return mailSourceFetchService;
    }

    public record MailboxCountProbe(String folderName, int messageCount) {
    }
}
