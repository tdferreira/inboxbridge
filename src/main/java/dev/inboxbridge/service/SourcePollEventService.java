package dev.inboxbridge.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.persistence.SourcePollEvent;
import dev.inboxbridge.persistence.SourcePollEventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SourcePollEventService {
    private static final String STOPPED_BY_USER_MESSAGE = "Stopped by user.";

    @Inject
    SourcePollEventRepository repository;

    @Transactional
    public void record(
            String sourceId,
            String trigger,
            Instant startedAt,
            Instant finishedAt,
            int fetched,
            int imported,
            int duplicates,
            int spamJunkMessageCount,
            String actorUsername,
            String executionSurface,
            String error) {
        SourcePollEvent event = new SourcePollEvent();
        event.sourceId = sourceId;
        event.triggerName = trigger;
        event.status = statusFor(error);
        event.startedAt = startedAt;
        event.finishedAt = finishedAt;
        event.fetchedCount = fetched;
        event.importedCount = imported;
        event.duplicateCount = duplicates;
        event.spamJunkMessageCount = Math.max(0, spamJunkMessageCount);
        event.actorUsername = actorUsername == null || actorUsername.isBlank() ? null : actorUsername;
        event.executionSurface = executionSurface == null || executionSurface.isBlank() ? null : executionSurface;
        event.errorMessage = error;
        repository.persist(event);
    }

    private String statusFor(String error) {
        if (error == null || error.isBlank()) {
            return "SUCCESS";
        }
        if (STOPPED_BY_USER_MESSAGE.equals(error.trim())) {
            return "STOPPED";
        }
        return "ERROR";
    }

    public Optional<AdminPollEventSummary> latestForSource(String sourceId) {
        return repository.findLatestBySourceId(sourceId).map(this::toSummary);
    }

    public List<AdminPollEventSummary> recentEvents(int limit) {
        return repository.listRecent(limit).stream()
                .map(this::toSummary)
                .toList();
    }

    public List<SourcePollEvent> listSince(Instant since) {
        return repository.listSince(since);
    }

    public List<SourcePollEvent> listBySourceIdsSince(List<String> sourceIds, Instant since) {
        return repository.listBySourceIdsSince(sourceIds, since);
    }

    private AdminPollEventSummary toSummary(SourcePollEvent event) {
        return new AdminPollEventSummary(
                event.sourceId,
                event.triggerName,
                event.status,
                event.startedAt,
                event.finishedAt,
                event.fetchedCount,
                event.importedCount,
                event.duplicateCount,
                event.spamJunkMessageCount,
                event.actorUsername,
                event.executionSurface,
                event.errorMessage);
    }
}
