package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SourcePollEventRepository implements PanacheRepository<SourcePollEvent> {

    public Optional<SourcePollEvent> findLatestBySourceId(String sourceId) {
        return find("sourceId = ?1 order by finishedAt desc", sourceId)
                .page(0, 1)
                .list()
                .stream()
                .findFirst();
    }

    public List<SourcePollEvent> listRecent(int limit) {
        return find("order by finishedAt desc")
                .page(0, limit)
                .list();
    }

    public List<SourcePollEvent> listSince(Instant since) {
        return find("finishedAt >= ?1 order by finishedAt", since)
                .list();
    }

    public List<SourcePollEvent> listBySourceIdsSince(List<String> sourceIds, Instant since) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        return find("sourceId in ?1 and finishedAt >= ?2 order by finishedAt", sourceIds, since)
                .list();
    }
}
