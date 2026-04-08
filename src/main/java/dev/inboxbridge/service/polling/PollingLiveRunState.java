package dev.inboxbridge.service.polling;

import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mutable in-memory model for one live poll run and its per-source state.
 * Keeping it outside {@link PollingLiveService} lets the service focus on
 * coordination, subscriptions, and control flow instead of also owning the
 * full state structure.
 */
final class PollingLiveRunState {

    final String runId = UUID.randomUUID().toString();
    final String trigger;
    final Instant startedAt = Instant.now();
    final Long actorUserId;
    final String actorUsername;
    final boolean actorAdmin;
    final LinkedHashMap<String, SourceState> sourcesById = new LinkedHashMap<>();
    final ArrayDeque<String> queue = new ArrayDeque<>();
    final LinkedHashSet<String> activeSourceIds = new LinkedHashSet<>();
    final List<Runnable> cancellationActions = new ArrayList<>();

    String state = "RUNNING";
    boolean pauseRequested;
    boolean stopRequested;
    String stopRequestedByUsername;
    Instant updatedAt = startedAt;

    PollingLiveRunState(String trigger, AppUser actor, List<RuntimeEmailAccount> emailAccounts) {
        this.trigger = trigger;
        this.actorUserId = actor == null ? null : actor.id;
        this.actorUsername = actor == null ? "system" : actor.username;
        this.actorAdmin = actor != null && actor.role == AppUser.Role.ADMIN;
        for (RuntimeEmailAccount emailAccount : emailAccounts) {
            SourceState source = new SourceState(emailAccount);
            sourcesById.put(emailAccount.id(), source);
            queue.addLast(emailAccount.id());
        }
    }

    String primaryActiveSourceId() {
        return activeSourceIds.stream().findFirst().orElse(null);
    }

    static final class SourceState {
        final String sourceId;
        final Long ownerUserId;
        final String ownerUsername;
        final String label;

        String state = "QUEUED";
        int attempt = 1;
        int totalMessages;
        long totalBytes;
        long processedBytes;
        int fetched;
        int imported;
        int duplicates;
        String error;
        Instant startedAt;
        Instant finishedAt;

        SourceState(RuntimeEmailAccount emailAccount) {
            this.sourceId = emailAccount.id();
            this.ownerUserId = emailAccount.ownerUserId();
            this.ownerUsername = emailAccount.ownerUsername();
            this.label = emailAccount.customLabel().orElse(emailAccount.id());
        }

        boolean isQueued() {
            return "QUEUED".equals(state) || "RETRY_QUEUED".equals(state);
        }

        int position(ArrayDeque<String> queue, Set<String> activeSourceIds) {
            if (activeSourceIds.contains(sourceId)) {
                return 0;
            }
            int index = 1;
            for (String queuedId : queue) {
                if (sourceId.equals(queuedId)) {
                    return index;
                }
                index += 1;
            }
            return Integer.MAX_VALUE;
        }

        boolean actionable(boolean admin, Long viewerId, Long actorUserId) {
            return admin || (actorUserId != null && actorUserId.equals(viewerId));
        }

        int processedMessages() {
            return Math.max(0, fetched);
        }
    }
}
