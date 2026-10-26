package dev.inboxbridge.service.mail;

import java.time.Duration;
import dev.inboxbridge.service.polling.PollingService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;

/** Test-only execution clock; production configuration remains above the shared minimum. */
public final class AcceleratedIdleWatcher extends ImapIdleWatchService implements AutoCloseable {
    public AcceleratedIdleWatcher(RuntimeEmailAccountService accounts, PollingService pipeline, ImapIdleHealthService health) {
        runtimeEmailAccountService = accounts; pollingService = pipeline; imapIdleHealthService = health;
        mailSessionFactory = MailSourceStandaloneFactory.mailSessionFactory();
        idleRenewalInterval = Duration.ofMinutes(1); idleRenewalTimeout = Duration.ofMillis(700);
    }
    @Override protected Duration renewalExecutionInterval(Duration configured) { return Duration.ofMillis(250); }
    public void refresh() { refreshWatches(); }
    public void drain() { drainPendingSources(); }
    @Override public void close() { shutdown(); }
}
