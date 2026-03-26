package dev.connexa.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ImportedMessageRepository implements PanacheRepository<ImportedMessage> {

    public boolean existsBySourceMessageKey(String sourceAccountId, String sourceMessageKey) {
        return count("sourceAccountId = ?1 and sourceMessageKey = ?2", sourceAccountId, sourceMessageKey) > 0;
    }

    public boolean existsByRawSha256(String rawSha256) {
        return count("rawSha256", rawSha256) > 0;
    }

    public Optional<ImportedMessage> findBySourceMessageKey(String sourceAccountId, String sourceMessageKey) {
        return find("sourceAccountId = ?1 and sourceMessageKey = ?2", sourceAccountId, sourceMessageKey)
                .firstResultOptional();
    }
}
