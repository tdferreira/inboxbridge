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
            String error) {
        SourcePollEvent event = new SourcePollEvent();
        event.sourceId = sourceId;
        event.triggerName = trigger;
        event.status = error == null ? "SUCCESS" : "ERROR";
        event.startedAt = startedAt;
        event.finishedAt = finishedAt;
        event.fetchedCount = fetched;
        event.importedCount = imported;
        event.duplicateCount = duplicates;
        event.errorMessage = error;
        repository.persist(event);
    }

    public Optional<AdminPollEventSummary> latestForSource(String sourceId) {
        return repository.findLatestBySourceId(sourceId).map(this::toSummary);
    }

    public List<AdminPollEventSummary> recentEvents(int limit) {
        return repository.listRecent(limit).stream()
                .map(this::toSummary)
                .toList();
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
                event.errorMessage);
    }
}
