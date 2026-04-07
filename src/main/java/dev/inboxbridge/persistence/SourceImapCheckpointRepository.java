package dev.inboxbridge.persistence;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SourceImapCheckpointRepository implements PanacheRepository<SourceImapCheckpoint> {

    public Optional<SourceImapCheckpoint> findByScope(String sourceId, String destinationKey, String folderName) {
        return find("sourceId = ?1 and destinationKey = ?2 and lower(folderName) = lower(?3)",
                sourceId,
                destinationKey,
                folderName)
                .firstResultOptional();
    }

    public List<SourceImapCheckpoint> listBySourceIds(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return List.of();
        }
        return list("sourceId in ?1", sourceIds);
    }

    public long deleteBySourceIds(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return 0;
        }
        return delete("sourceId in ?1", sourceIds);
    }
}
