package dev.inboxbridge.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SourcePollingStateRepository implements PanacheRepository<SourcePollingState> {

    public Optional<SourcePollingState> findBySourceId(String sourceId) {
        return find("sourceId", sourceId).firstResultOptional();
    }

    public Map<String, SourcePollingState> findBySourceIds(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Map.of();
        }
        return list("sourceId in ?1", sourceIds).stream()
                .collect(Collectors.toMap(state -> state.sourceId, Function.identity()));
    }

    public long deleteBySourceIds(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return 0;
        }
        return delete("sourceId in ?1", sourceIds);
    }
}
