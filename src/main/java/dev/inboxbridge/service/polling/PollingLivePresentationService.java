package dev.inboxbridge.service.polling;

import dev.inboxbridge.service.*;

import dev.inboxbridge.dto.LiveNotificationContentView;
import dev.inboxbridge.dto.LiveNotificationView;
import dev.inboxbridge.dto.PollLiveSourceView;
import dev.inboxbridge.dto.PollLiveView;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Shapes live-poll state into viewer-facing snapshots and notifications so the
 * coordinator service can focus on control flow and subscriber fan-out.
 */
@ApplicationScoped
public class PollingLivePresentationService {

    public PollLiveView buildView(PollingLiveRunState state, Long viewerId, boolean admin) {
        List<PollLiveSourceView> sources = state.sourcesById.values().stream()
                .filter((source) -> admin || (source.ownerUserId != null && source.ownerUserId.equals(viewerId)))
                .sorted(Comparator
                        .comparingInt((PollingLiveRunState.SourceState source) -> source.position(state.queue, state.activeSourceIds))
                        .thenComparing((source) -> source.label))
                .map((source) -> new PollLiveSourceView(
                        source.sourceId,
                        source.ownerUsername,
                        source.label,
                        source.state,
                        source.actionable(admin, viewerId, state.actorUserId),
                        source.position(state.queue, state.activeSourceIds),
                        source.attempt,
                        source.totalMessages,
                        source.processedMessages(),
                        source.totalBytes,
                        source.processedBytes,
                        source.fetched,
                        source.imported,
                        source.duplicates,
                        source.error,
                        source.startedAt,
                        source.finishedAt))
                .toList();
        if (!admin && sources.isEmpty()) {
            return null;
        }
        boolean viewerCanControl = admin || (state.actorUserId != null && state.actorUserId.equals(viewerId));
        return new PollLiveView(
                true,
                state.runId,
                state.state,
                state.trigger,
                state.actorUsername,
                viewerCanControl,
                admin ? state.primaryActiveSourceId() : activeVisibleSourceId(state, viewerId),
                state.startedAt,
                state.updatedAt,
                sources);
    }

    public LiveNotificationView notificationForRunStarted(PollingLiveRunState state) {
        if (!shouldNotifyForRun(state)) {
            return null;
        }
        String key = isGlobalRun(state) ? "notifications.pollStarted" : "notifications.userPollStarted";
        String targetId = isGlobalRun(state) ? "system-dashboard-section" : "user-polling-section";
        return notification(key, "warning", isGlobalRun(state) ? "global-poll" : "user-poll", targetId, true);
    }

    public LiveNotificationView notificationForRunFinished(PollingLiveRunState state) {
        return null;
    }

    public LiveNotificationView notificationForSourceStarted(PollingLiveRunState state, PollingLiveRunState.SourceState source) {
        if (!shouldNotifyForRun(state) || !isSingleSourceRun(state)) {
            return null;
        }
        return notification(
                "notifications.fetcherPollStarted",
                "warning",
                "fetcher-poll:" + source.sourceId,
                "source-email-accounts-section",
                false,
                Map.of("emailAccountId", source.sourceId));
    }

    public LiveNotificationView notificationForSourceFinished(PollingLiveRunState state, PollingLiveRunState.SourceState source) {
        if (!shouldNotifyForRun(state) || isSkipReason(source.error)) {
            return null;
        }
        if (source.error == null && !isSingleSourceRun(state)) {
            return null;
        }
        String key = source.error == null ? "notifications.livePollSourceFinished" : "notifications.livePollSourceFailed";
        return notification(
                key,
                source.error == null ? "success" : "error",
                "fetcher-poll:" + source.sourceId,
                "source-email-accounts-section",
                true,
                Map.of(
                        "emailAccountId", source.sourceId,
                        "fetched", String.valueOf(source.fetched),
                        "imported", String.valueOf(source.imported),
                        "duplicates", String.valueOf(source.duplicates)));
    }

    public LiveNotificationView newSignInNotification(
            SessionLocationAlertService.SessionLocationAssessment assessment,
            PollingLiveService.SessionStreamKind streamKind,
            Long sessionId) {
        String notificationKey = "notifications.newSessionDetected";
        if (assessment.locationLabel() != null && assessment.unusualLocation()) {
            notificationKey = "notifications.newSessionDetectedFromUnusualLocation";
        } else if (assessment.locationLabel() != null) {
            notificationKey = "notifications.newSessionDetectedFromLocation";
        }
        return notification(
                notificationKey,
                "warning",
                "session-activity",
                buildRecentSessionTargetId(streamKind, sessionId),
                false,
                assessment.locationLabel() == null
                        ? Map.of()
                        : Map.of("location", assessment.locationLabel()));
    }

    public String buildRecentSessionTargetId(PollingLiveService.SessionStreamKind streamKind, Long sessionId) {
        return "recent-session-" + streamKind.name() + "-" + sessionId;
    }

    private boolean shouldNotifyForRun(PollingLiveRunState state) {
        return state != null && !"scheduler".equals(state.trigger);
    }

    private boolean isGlobalRun(PollingLiveRunState state) {
        return state != null && "admin-ui".equals(state.trigger);
    }

    private boolean isSingleSourceRun(PollingLiveRunState state) {
        if (state == null || state.trigger == null) {
            return false;
        }
        return "app-fetcher".equals(state.trigger)
                || "admin-fetcher".equals(state.trigger)
                || state.trigger.startsWith("remote-source");
    }

    private boolean isSkipReason(String error) {
        return "disabled".equals(error)
                || "DISABLED".equals(error)
                || "COOLDOWN".equals(error)
                || "INTERVAL".equals(error);
    }

    private LiveNotificationView notification(String key, String tone, String groupKey, String targetId) {
        return notification(key, tone, groupKey, targetId, false, Map.of());
    }

    private LiveNotificationView notification(String key, String tone, String groupKey, String targetId, boolean replaceGroup) {
        return notification(key, tone, groupKey, targetId, replaceGroup, Map.of());
    }

    private LiveNotificationView notification(
            String key,
            String tone,
            String groupKey,
            String targetId,
            boolean replaceGroup,
            Map<String, String> params) {
        LiveNotificationContentView content = new LiveNotificationContentView("translation", key, params);
        return new LiveNotificationView(null, content, groupKey, content, replaceGroup, List.of(), targetId, tone);
    }

    private String activeVisibleSourceId(PollingLiveRunState state, Long viewerId) {
        for (String activeSourceId : state.activeSourceIds) {
            PollingLiveRunState.SourceState source = state.sourcesById.get(activeSourceId);
            if (source == null) {
                continue;
            }
            if (source.ownerUserId != null && source.ownerUserId.equals(viewerId)) {
                return source.sourceId;
            }
        }
        return null;
    }
}
