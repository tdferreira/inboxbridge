package dev.inboxbridge.service.extension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.ExtensionLastRunSummaryView;
import dev.inboxbridge.dto.ExtensionPollStateView;
import dev.inboxbridge.dto.ExtensionSourceStatusView;
import dev.inboxbridge.dto.ExtensionStatusView;
import dev.inboxbridge.dto.ExtensionSummaryView;
import dev.inboxbridge.dto.ExtensionUserView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.polling.SourcePollEventService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import dev.inboxbridge.service.user.UserUiPreferenceService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Shapes the small browser-extension status payload from durable poll history
 * plus the user's current live-poll snapshot.
 */
@ApplicationScoped
public class ExtensionStatusService {

    @Inject
    RuntimeEmailAccountService runtimeEmailAccountService;

    @Inject
    SourcePollEventService sourcePollEventService;

    @Inject
    PollingLiveService pollingLiveService;

    @Inject
    UserUiPreferenceService userUiPreferenceService;

    public ExtensionStatusView statusForUser(AppUser user) {
        List<RuntimeEmailAccount> accessibleSources = runtimeEmailAccountService.listEnabledForUser(user);
        List<ExtensionSourceStatusView> sources = new ArrayList<>();

        int enabledSourceCount = 0;
        int errorSourceCount = 0;
        Instant lastCompletedRunAt = null;

        for (RuntimeEmailAccount source : accessibleSources) {
            if (source.enabled()) {
                enabledSourceCount++;
            }

            var lastEvent = sourcePollEventService.latestForSource(source.id()).orElse(null);
            String status = source.enabled() ? "IDLE" : "DISABLED";
            Instant lastRunAt = null;
            int fetched = 0;
            int imported = 0;
            int duplicates = 0;
            String lastError = null;
            boolean needsAttention = false;

            if (lastEvent != null) {
                status = switch (String.valueOf(lastEvent.status())) {
                    case "ERROR" -> "ERROR";
                    case "SUCCESS" -> "SUCCESS";
                    case "STOPPED" -> "STOPPED";
                    default -> status;
                };
                lastRunAt = lastEvent.finishedAt();
                fetched = lastEvent.fetched();
                imported = lastEvent.imported();
                duplicates = lastEvent.duplicates();
                lastError = compactError(lastEvent.error());
                needsAttention = "ERROR".equals(lastEvent.status()) && lastError != null;
                if (needsAttention) {
                    errorSourceCount++;
                }
                if (lastRunAt != null && (lastCompletedRunAt == null || lastRunAt.isAfter(lastCompletedRunAt))) {
                    lastCompletedRunAt = lastRunAt;
                }
            }

            sources.add(new ExtensionSourceStatusView(
                    source.id(),
                    source.id(),
                    source.enabled(),
                    status,
                    lastRunAt,
                    fetched,
                    imported,
                    duplicates,
                    lastError,
                    needsAttention));
        }

        var live = pollingLiveService.snapshotFor(user);
        for (int index = 0; index < sources.size(); index++) {
            ExtensionSourceStatusView source = sources.get(index);
            if (live.sources().stream().anyMatch(candidate -> candidate.sourceId().equals(source.sourceId()) && "RUNNING".equals(candidate.state()))) {
                sources.set(index, new ExtensionSourceStatusView(
                        source.sourceId(),
                        source.label(),
                        source.enabled(),
                        "RUNNING",
                        source.lastRunAt(),
                        source.lastFetched(),
                        source.lastImported(),
                        source.lastDuplicates(),
                        source.lastError(),
                        source.needsAttention()));
            }
        }

        int lastFetched = 0;
        int lastImported = 0;
        int lastDuplicates = 0;
        int lastErrors = 0;
        if (lastCompletedRunAt != null) {
            for (ExtensionSourceStatusView source : sources) {
                if (lastCompletedRunAt.equals(source.lastRunAt())) {
                    lastFetched += source.lastFetched();
                    lastImported += source.lastImported();
                    lastDuplicates += source.lastDuplicates();
                    if ("ERROR".equals(source.status())) {
                        lastErrors++;
                    }
                }
            }
        }

        sources.sort(Comparator
                .comparing(ExtensionSourceStatusView::needsAttention).reversed()
                .thenComparing(ExtensionSourceStatusView::sourceId));

        var uiPreference = userUiPreferenceService.viewForUser(user.id)
                .orElseGet(userUiPreferenceService::defaultView);

        return new ExtensionStatusView(
                new ExtensionUserView(
                        user.username,
                        user.username,
                        uiPreference.language(),
                        uiPreference.themeMode()),
                new ExtensionPollStateView(
                        live.running(),
                        live.state(),
                        !live.running(),
                        live.activeSourceId(),
                        live.startedAt(),
                        live.updatedAt()),
                new ExtensionSummaryView(
                        sources.size(),
                        enabledSourceCount,
                        errorSourceCount,
                        lastCompletedRunAt,
                        lastCompletedRunAt == null
                                ? null
                                : new ExtensionLastRunSummaryView(lastFetched, lastImported, lastDuplicates, lastErrors)),
                sources);
    }

    private String compactError(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200);
    }
}
