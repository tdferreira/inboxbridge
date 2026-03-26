package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ImportedMessageRepository implements PanacheRepository<ImportedMessage> {

    public boolean existsBySourceMessageKey(String destinationKey, String sourceAccountId, String sourceMessageKey) {
        return count("destinationKey = ?1 and sourceAccountId = ?2 and sourceMessageKey = ?3", destinationKey, sourceAccountId, sourceMessageKey) > 0;
    }

    public boolean existsByRawSha256(String destinationKey, String rawSha256) {
        return count("destinationKey = ?1 and rawSha256 = ?2", destinationKey, rawSha256) > 0;
    }

    public Optional<ImportedMessage> findBySourceMessageKey(String destinationKey, String sourceAccountId, String sourceMessageKey) {
        return find("destinationKey = ?1 and sourceAccountId = ?2 and sourceMessageKey = ?3", destinationKey, sourceAccountId, sourceMessageKey)
                .firstResultOptional();
    }

    public List<Object[]> summarizeBySource() {
        return getEntityManager().createQuery(
                "select im.sourceAccountId, count(im), max(im.importedAt) from ImportedMessage im group by im.sourceAccountId",
                Object[].class)
                .getResultList();
    }
}
