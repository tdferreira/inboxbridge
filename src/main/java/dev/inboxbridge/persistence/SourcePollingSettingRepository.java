package dev.inboxbridge.persistence;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class SourcePollingSettingRepository implements PanacheRepository<SourcePollingSetting> {

    public Optional<SourcePollingSetting> findBySourceId(String sourceId) {
        return find("sourceId", sourceId).firstResultOptional();
    }

    @Transactional
    public long deleteBySourceIds(List<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return 0;
        }
        return delete("sourceId in ?1", sourceIds);
    }
}
