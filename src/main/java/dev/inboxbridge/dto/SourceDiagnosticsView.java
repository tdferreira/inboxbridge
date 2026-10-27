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
        boolean idleSafetyPollingEnabled,
        List<SourceIdleWatchView> idleWatches,
        java.time.Instant nextIdleSafetyPollAt) {
    public SourceDiagnosticsView(String destinationIdentityKey,
        String popLastSeenUidl,
        List<SourceImapCheckpointView> imapCheckpoints,
        SourceThrottleStateView sourceThrottle,
        SourceThrottleStateView destinationThrottle,
        boolean idleHealthy,
        boolean idleSchedulerFallback,
        boolean idleSafetyPollingEnabled,
        List<SourceIdleWatchView> idleWatches) {
        this(destinationIdentityKey, popLastSeenUidl, imapCheckpoints, sourceThrottle, destinationThrottle, idleHealthy, idleSchedulerFallback, idleSafetyPollingEnabled, idleWatches, null);
    }

}
