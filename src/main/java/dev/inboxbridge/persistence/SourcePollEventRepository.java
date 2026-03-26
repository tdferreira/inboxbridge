package dev.inboxbridge.persistence;

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
}
