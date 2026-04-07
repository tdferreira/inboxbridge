package dev.inboxbridge.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.SourceDiagnosticsView;
import dev.inboxbridge.dto.SourceIdleWatchView;
import dev.inboxbridge.dto.SourceImapCheckpointView;
import dev.inboxbridge.dto.SourceThrottleStateView;
import dev.inboxbridge.persistence.PollThrottleState;
import dev.inboxbridge.persistence.PollThrottleStateRepository;
import dev.inboxbridge.persistence.SourceImapCheckpoint;
import dev.inboxbridge.persistence.SourceImapCheckpointRepository;
import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.service.destination.DestinationIdentityKeys;
import dev.inboxbridge.persistence.SourcePollingStateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Builds operator-facing source diagnostics from the persisted scheduler,
 * checkpoint, adaptive-throttle, and IMAP IDLE runtime state.
 */
@ApplicationScoped
public class SourceDiagnosticsService {

    @Inject
    SourcePollingStateRepository sourcePollingStateRepository;

    @Inject
    SourceImapCheckpointRepository sourceImapCheckpointRepository;

    @Inject
    PollThrottleStateRepository pollThrottleStateRepository;

    @Inject
    ImapIdleHealthService imapIdleHealthService;

    public Map<String, SourceDiagnosticsView> viewByRuntimeAccounts(List<RuntimeEmailAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return Map.of();
        }
        List<RuntimeEmailAccount> distinctAccounts = accounts.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(RuntimeEmailAccount::id, account -> account, (left, right) -> left, LinkedHashMap::new))
                .values()
                .stream()
                .toList();
        List<String> sourceIds = distinctAccounts.stream()
                .map(RuntimeEmailAccount::id)
                .toList();
        Map<String, SourcePollingState> pollingStates = sourcePollingStateRepository == null
                ? Map.of()
                : sourcePollingStateRepository.findBySourceIds(sourceIds);
        Map<String, List<SourceImapCheckpoint>> imapCheckpoints = sourceImapCheckpointRepository == null
                ? Map.of()
                : sourceImapCheckpointRepository.listBySourceIds(sourceIds).stream()
                        .collect(Collectors.groupingBy(checkpoint -> checkpoint.sourceId));

        Map<String, SourceDiagnosticsView> result = new LinkedHashMap<>();
        for (RuntimeEmailAccount account : distinctAccounts) {
            SourcePollingState state = pollingStates.get(account.id());
            String destinationIdentityKey = DestinationIdentityKeys.forTarget(account.destination());
            result.put(account.id(), new SourceDiagnosticsView(
                    destinationIdentityKey,
                    popCheckpointFor(state, destinationIdentityKey),
                    imapCheckpointViews(account, state, imapCheckpoints.getOrDefault(account.id(), List.of()), destinationIdentityKey),
                    throttleView(PollThrottleKeys.sourceMailbox(account)),
                    throttleView(PollThrottleKeys.destination(account.destination())),
                    idleHealthy(account),
                    idleSchedulerFallback(account),
                    idleWatchViews(account)));
        }
        return result;
    }

    private String popCheckpointFor(SourcePollingState state, String destinationIdentityKey) {
        if (state == null || destinationIdentityKey == null || destinationIdentityKey.isBlank()) {
            return null;
        }
        if (!destinationIdentityKey.equals(state.popCheckpointDestinationKey)) {
            return null;
        }
        return state.popLastSeenUidl == null || state.popLastSeenUidl.isBlank() ? null : state.popLastSeenUidl;
    }

    private List<SourceImapCheckpointView> imapCheckpointViews(
            RuntimeEmailAccount account,
            SourcePollingState state,
            List<SourceImapCheckpoint> persisted,
            String destinationIdentityKey) {
        if (account.protocol() != InboxBridgeConfig.Protocol.IMAP || destinationIdentityKey == null || destinationIdentityKey.isBlank()) {
            return List.of();
        }
        List<SourceImapCheckpointView> checkpointViews = persisted.stream()
                .filter(checkpoint -> destinationIdentityKey.equals(checkpoint.destinationKey))
                .map(checkpoint -> new SourceImapCheckpointView(
                        checkpoint.folderName,
                        checkpoint.uidValidity,
                        checkpoint.lastSeenUid,
                        checkpoint.updatedAt))
                .sorted(Comparator.comparing(SourceImapCheckpointView::folderName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (!checkpointViews.isEmpty()) {
            return checkpointViews;
        }
        if (state == null
                || state.imapFolderName == null
                || state.imapUidValidity == null
                || state.imapLastSeenUid == null
                || !destinationIdentityKey.equals(state.imapCheckpointDestinationKey)) {
            return List.of();
        }
        return List.of(new SourceImapCheckpointView(
                state.imapFolderName,
                state.imapUidValidity,
                state.imapLastSeenUid,
                state.updatedAt));
    }

    private SourceThrottleStateView throttleView(String throttleKey) {
        if (throttleKey == null || pollThrottleStateRepository == null) {
            return null;
        }
        PollThrottleState state = pollThrottleStateRepository.findByThrottleKey(throttleKey).orElse(null);
        if (state == null) {
            return null;
        }
        return new SourceThrottleStateView(
                state.throttleKey,
                state.throttleKind,
                Math.max(1, state.adaptiveMultiplier),
                state.nextAllowedAt,
                state.updatedAt);
    }

    private boolean idleHealthy(RuntimeEmailAccount account) {
        if (!usesImapIdle(account) || imapIdleHealthService == null) {
            return false;
        }
        return imapIdleHealthService.isHealthy(account.id());
    }

    private boolean idleSchedulerFallback(RuntimeEmailAccount account) {
        if (!usesImapIdle(account) || imapIdleHealthService == null) {
            return false;
        }
        return imapIdleHealthService.shouldSchedulerFallback(account.id(), null);
    }

    private List<SourceIdleWatchView> idleWatchViews(RuntimeEmailAccount account) {
        if (!usesImapIdle(account) || imapIdleHealthService == null) {
            return List.of();
        }
        return imapIdleHealthService.watchStates(account.id()).stream()
                .map(state -> new SourceIdleWatchView(
                        folderNameFromWatchKey(state.watchKey()),
                        idleStatusFor(state),
                        state.lastConnectedAt(),
                        state.disconnectedSince()))
                .sorted(Comparator.comparing(SourceIdleWatchView::folderName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private boolean usesImapIdle(RuntimeEmailAccount account) {
        return account != null
                && account.protocol() == InboxBridgeConfig.Protocol.IMAP
                && account.fetchMode() == dev.inboxbridge.domain.SourceFetchMode.IDLE;
    }

    private String idleStatusFor(ImapIdleHealthService.WatchHealth state) {
        if (state.disconnectedSince() == null) {
            return "CONNECTED";
        }
        if (state.lastConnectedAt() == null) {
            return "TRACKING";
        }
        return "DISCONNECTED";
    }

    private String folderNameFromWatchKey(String watchKey) {
        if (watchKey == null || watchKey.isBlank()) {
            return "INBOX";
        }
        int separator = watchKey.indexOf('\n');
        if (separator < 0 || separator + 1 >= watchKey.length()) {
            return watchKey;
        }
        return watchKey.substring(separator + 1);
    }
}
