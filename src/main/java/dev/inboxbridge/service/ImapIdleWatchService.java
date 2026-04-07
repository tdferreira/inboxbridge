package dev.inboxbridge.service;

import dev.inboxbridge.service.polling.PollingService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.angus.mail.imap.IMAPFolder;
import org.jboss.logging.Logger;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceMailboxFolders;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.dto.PollRunError;
import dev.inboxbridge.dto.PollRunResult;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.event.MessageCountAdapter;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.Message;

@ApplicationScoped
public class ImapIdleWatchService {

    private static final Logger LOG = Logger.getLogger(ImapIdleWatchService.class);
    private static final Duration MIN_RECONNECT_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RECONNECT_DELAY = Duration.ofMinutes(1);

    @Inject
    RuntimeEmailAccountService runtimeEmailAccountService;

    @Inject
    PollingService pollingService;

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    ImapIdleHealthService imapIdleHealthService;

    @Inject
    MailSessionFactory mailSessionFactory;

    private final Map<String, IdleWatchHandle> handles = new ConcurrentHashMap<>();
    private final Set<String> pendingSources = ConcurrentHashMap.newKeySet();

    void onStart(@Observes StartupEvent ignored) {
        refreshWatches();
    }

    void onSourceMailboxConfigurationChanged(
            @Observes(during = TransactionPhase.AFTER_SUCCESS) SourceMailboxConfigurationChanged ignored) {
        refreshWatches();
    }

    @PreDestroy
    void shutdown() {
        if (imapIdleHealthService != null) {
            new ArrayList<>(handles.entrySet()).forEach(entry ->
                    imapIdleHealthService.clear(entry.getValue().source.id(), entry.getKey()));
        }
        handles.values().forEach(IdleWatchHandle::stop);
        handles.clear();
        pendingSources.clear();
    }

    @Scheduled(every = "30s")
    void refreshScheduled() {
        refreshWatches();
    }

    @Scheduled(every = "5s")
    void drainPendingSources() {
        if (pollingService == null) {
            return;
        }
        List<String> sourceIds = new ArrayList<>(pendingSources);
        if (!sourceIds.isEmpty()) {
            LOG.infof("IMAP IDLE drain examining %d queued source(s): %s", sourceIds.size(), sourceIds);
        }
        for (String sourceId : sourceIds) {
            if (!pendingSources.remove(sourceId)) {
                continue;
            }
            RuntimeEmailAccount source = currentIdleSources().stream()
                    .filter(candidate -> candidate.id().equals(sourceId))
                    .findFirst()
                    .orElse(null);
            if (source == null) {
                LOG.warnf("IMAP IDLE drain dropped queued source %s because it is no longer eligible", sourceId);
                continue;
            }
            LOG.infof("IMAP IDLE drain starting queued sync for %s", source.id());
            PollRunResult result = pollingService.runIdleTriggeredPollForSource(source);
            if (isBusyResult(result, source.id())) {
                LOG.infof("IMAP IDLE drain re-queueing %s because the poll runner was busy", source.id());
                pendingSources.add(source.id());
            }
        }
    }

    private boolean isBusyResult(PollRunResult result, String sourceId) {
        if (result == null) {
            return false;
        }
        for (PollRunError error : result.getErrorDetails()) {
            if (error != null
                    && "poll_busy".equals(error.code())
                    && (sourceId == null || sourceId.equals(error.sourceId()))) {
                return true;
            }
        }
        return false;
    }

    synchronized void refreshWatches() {
        Instant now = Instant.now();
        Map<String, WatchTarget> desired = currentIdleSources().stream()
                .flatMap(source -> source.sourceFolders().stream()
                        .map(folderName -> new WatchTarget(watchKey(source.id(), folderName), source, folderName)))
                .collect(java.util.stream.Collectors.toMap(WatchTarget::watchKey, target -> target));

        for (Map.Entry<String, IdleWatchHandle> entry : new ArrayList<>(handles.entrySet())) {
            WatchTarget next = desired.get(entry.getKey());
            if (next == null || !entry.getValue().matches(next.source(), next.folderName())) {
                entry.getValue().stop();
                handles.remove(entry.getKey());
                if (imapIdleHealthService != null) {
                    imapIdleHealthService.clear(entry.getValue().source.id(), entry.getKey());
                }
            }
        }

        for (WatchTarget target : desired.values()) {
            if (imapIdleHealthService != null) {
                imapIdleHealthService.ensureTracked(target.source().id(), target.watchKey(), now);
            }
            handles.computeIfAbsent(target.watchKey(), ignored -> {
                IdleWatchHandle handle = new IdleWatchHandle(target.source(), target.folderName());
                handle.start();
                pendingSources.add(target.source().id());
                return handle;
            });
        }
    }

    private List<RuntimeEmailAccount> currentIdleSources() {
        return runtimeEmailAccountService.listEnabledForPolling().stream()
                .filter(source -> source.enabled()
                        && source.protocol() == InboxBridgeConfig.Protocol.IMAP
                        && source.fetchMode() == SourceFetchMode.IDLE)
                .toList();
    }

    private final class IdleWatchHandle {
        private final RuntimeEmailAccount source;
        private final String watchedFolderName;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private volatile Thread thread;
        private volatile Store store;
        private volatile Folder folder;
        private volatile int lastKnownMessageCount = -1;

        private IdleWatchHandle(RuntimeEmailAccount source, String watchedFolderName) {
            this.source = source;
            this.watchedFolderName = watchedFolderName;
        }

        private void start() {
            thread = Thread.startVirtualThread(this::runLoop);
        }

        private boolean matches(RuntimeEmailAccount candidate, String candidateFolderName) {
            return source.id().equals(candidate.id())
                    && source.enabled() == candidate.enabled()
                    && source.protocol() == candidate.protocol()
                    && source.fetchMode() == candidate.fetchMode()
                    && source.host().equals(candidate.host())
                    && source.port() == candidate.port()
                    && source.tls() == candidate.tls()
                    && source.authMethod() == candidate.authMethod()
                    && source.oauthProvider() == candidate.oauthProvider()
                    && source.username().equals(candidate.username())
                    && source.password().equals(candidate.password())
                    && source.oauthRefreshToken().equals(candidate.oauthRefreshToken())
                    && watchedFolderName.equals(candidateFolderName);
        }

        private void stop() {
            active.set(false);
            closeQuietly(folder);
            closeQuietly(store);
            Thread currentThread = thread;
            if (currentThread != null) {
                currentThread.interrupt();
            }
        }

        private void runLoop() {
            Duration delay = MIN_RECONNECT_DELAY;
            while (active.get()) {
                try {
                    watchUntilInterrupted();
                    delay = MIN_RECONNECT_DELAY;
                } catch (RuntimeException error) {
                    if (!active.get()) {
                        return;
                    }
                    LOG.warnf(error, "IMAP IDLE watcher for %s disconnected; retrying soon", source.id());
                    sleep(delay);
                    delay = delay.multipliedBy(2);
                    if (delay.compareTo(MAX_RECONNECT_DELAY) > 0) {
                        delay = MAX_RECONNECT_DELAY;
                    }
                }
            }
        }

        private void watchUntilInterrupted() {
            String folderName = watchedFolderName;
            try {
                Session session = mailSessionFactory().idleImapSession(source);
                store = session.getStore(mailSessionFactory().imapStoreProtocol(source.tls()));
                connectStore(store, source);
                folder = store.getFolder(folderName);
                if (folder == null || !folder.exists()) {
                    throw new IllegalStateException("The mailbox path " + folderName + " does not exist on " + source.host() + ".");
                }
                folder.open(Folder.READ_ONLY);
                lastKnownMessageCount = safeMessageCount(folder);
                if (imapIdleHealthService != null) {
                    imapIdleHealthService.markConnected(source.id(), watchKey(source.id(), folderName), Instant.now());
                }
                LOG.infof("IMAP IDLE watcher connected for %s on %s", source.id(), folderName);
                if (!(folder instanceof IMAPFolder imapFolder)) {
                    throw new IllegalStateException("IMAP IDLE requires an IMAP folder implementation");
                }
                folder.addMessageCountListener(new MessageCountAdapter() {
                    @Override
                    public void messagesAdded(MessageCountEvent event) {
                        int addedCount = event.getMessages() == null ? -1 : event.getMessages().length;
                        LOG.infof("IMAP IDLE watcher received messagesAdded for %s (%d new message(s))", source.id(), Math.max(addedCount, 0));
                        pendingSources.add(source.id());
                        refreshLastKnownMessageCount();
                    }
                });
                while (active.get() && store.isConnected() && folder.isOpen()) {
                    imapFolder.idle();
                    queueIfMailboxAdvanced();
                }
            } catch (MessagingException error) {
                throw new IllegalStateException("IMAP IDLE watcher failed for source " + source.id(), error);
            } finally {
                if (active.get() && imapIdleHealthService != null) {
                    imapIdleHealthService.markDisconnected(source.id(), watchKey(source.id(), folderName), Instant.now());
                }
                closeQuietly(folder);
                closeQuietly(store);
                folder = null;
                store = null;
                lastKnownMessageCount = -1;
            }
        }

        private void queueIfMailboxAdvanced() {
            Folder currentFolder = folder;
            if (currentFolder == null || !currentFolder.isOpen()) {
                return;
            }
            int latestCount = safeMessageCount(currentFolder);
            if (latestCount < 0) {
                return;
            }
            if (lastKnownMessageCount >= 0 && latestCount > lastKnownMessageCount) {
                LOG.infof(
                        "IMAP IDLE watcher detected mailbox growth for %s (%d -> %d); queueing sync",
                        source.id(),
                        lastKnownMessageCount,
                        latestCount);
                pendingSources.add(source.id());
            }
            lastKnownMessageCount = latestCount;
        }

        private void refreshLastKnownMessageCount() {
            Folder currentFolder = folder;
            if (currentFolder == null || !currentFolder.isOpen()) {
                return;
            }
            int latestCount = safeMessageCount(currentFolder);
            if (latestCount >= 0) {
                lastKnownMessageCount = latestCount;
            }
        }
    }

    private int safeMessageCount(Folder folder) {
        try {
            return folder.getMessageCount();
        } catch (MessagingException e) {
            LOG.debugf(e, "Unable to read message count for folder %s", safeFolderName(folder));
            return -1;
        }
    }

    private String safeFolderName(Folder folder) {
        if (folder == null) {
            return "<unknown>";
        }
        return folder.getFullName();
    }

    private String watchKey(String sourceId, String folderName) {
        return sourceId + "\n" + folderName;
    }

    private record WatchTarget(String watchKey, RuntimeEmailAccount source, String folderName) {
    }

    private void connectStore(Store store, RuntimeEmailAccount source) throws MessagingException {
        if (source.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && source.oauthProvider() == InboxBridgeConfig.OAuthProvider.MICROSOFT) {
            store.connect(source.host(), source.port(), source.username(), microsoftOAuthService.getAccessToken(source));
            return;
        }
        if (source.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && source.oauthProvider() == InboxBridgeConfig.OAuthProvider.GOOGLE) {
            store.connect(source.host(), source.port(), source.username(), googleOAuthService.getAccessToken(source));
            return;
        }
        store.connect(source.host(), source.port(), source.username(), source.password());
    }

    private void closeQuietly(Folder folder) {
        if (folder == null) {
            return;
        }
        try {
            if (folder.isOpen()) {
                folder.close(false);
            }
        } catch (MessagingException ignored) {
            // best effort while stopping a watcher
        }
    }

    private void closeQuietly(Store store) {
        if (store == null) {
            return;
        }
        try {
            store.close();
        } catch (MessagingException ignored) {
            // best effort while stopping a watcher
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(Math.max(1L, duration.toMillis()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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
}
