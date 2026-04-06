package dev.inboxbridge.dto;

import java.util.List;

/**
 * Operator-facing source diagnostics used by the admin UI to explain current
 * destination identity, checkpoints, persisted throttle state, and IMAP IDLE
 * watcher health without requiring direct database inspection.
 */
public record SourceDiagnosticsView(
        String destinationIdentityKey,
        String popLastSeenUidl,
        List<SourceImapCheckpointView> imapCheckpoints,
        SourceThrottleStateView sourceThrottle,
        SourceThrottleStateView destinationThrottle,
        boolean idleHealthy,
        boolean idleSchedulerFallback,
        List<SourceIdleWatchView> idleWatches) {
}
