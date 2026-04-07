package dev.inboxbridge.service.mail;

import java.time.Duration;

import dev.inboxbridge.config.MailClientConfig;
import dev.inboxbridge.service.oauth.GoogleOAuthService;
import dev.inboxbridge.service.oauth.MicrosoftOAuthService;
import dev.inboxbridge.service.MimeHashService;
import dev.inboxbridge.service.polling.PollCancellationService;
import dev.inboxbridge.service.polling.PollingSettingsService;
import dev.inboxbridge.service.polling.SourcePollingStateService;

/**
 * Builds mail-source collaborators for plain JVM callers such as focused unit
 * tests and GreenMail-backed integration tests where CDI is not bootstrapped.
 */
public final class MailSourceStandaloneFactory {

    private MailSourceStandaloneFactory() {
    }

    public static MailSourceClient client(
            PollingSettingsService pollingSettingsService,
            SourcePollingStateService sourcePollingStateService,
            MimeHashService mimeHashService,
            MicrosoftOAuthService microsoftOAuthService,
            GoogleOAuthService googleOAuthService,
            PollCancellationService pollCancellationService) {
        MailSessionFactory mailSessionFactory = mailSessionFactory();
        MailSourceConnectionService connectionService = new MailSourceConnectionService(
                microsoftOAuthService,
                googleOAuthService);
        MailSourceFolderService folderService = new MailSourceFolderService();
        MailSourceMessageMapper messageMapper = new MailSourceMessageMapper();
        messageMapper.mimeHashService = mimeHashService == null ? new MimeHashService() : mimeHashService;
        MailSourceCheckpointSelector checkpointSelector = new MailSourceCheckpointSelector();
        MailSourcePostPollActionService postPollActionService = new MailSourcePostPollActionService(
                mailSessionFactory,
                connectionService,
                messageMapper,
                pollCancellationService);
        MailSourceConnectionProbeService connectionProbeService = new MailSourceConnectionProbeService(
                mailSessionFactory,
                connectionService,
                folderService,
                messageMapper,
                pollCancellationService);
        MailSourceFetchService fetchService = new MailSourceFetchService(
                mailSessionFactory,
                connectionService,
                checkpointSelector,
                messageMapper,
                sourcePollingStateService,
                pollCancellationService);
        return new MailSourceClient(
                pollingSettingsService,
                postPollActionService,
                connectionProbeService,
                fetchService);
    }

    public static MailSessionFactory mailSessionFactory() {
        MailSessionFactory mailSessionFactory = new MailSessionFactory();
        mailSessionFactory.mailClientConfig = defaultMailClientConfig();
        return mailSessionFactory;
    }

    private static MailClientConfig defaultMailClientConfig() {
        return new MailClientConfig() {
            @Override
            public Duration connectionTimeout() {
                return Duration.ofSeconds(20);
            }

            @Override
            public Duration operationTimeout() {
                return Duration.ofSeconds(20);
            }

            @Override
            public Duration idleOperationTimeout() {
                return Duration.ZERO;
            }
        };
    }
}
