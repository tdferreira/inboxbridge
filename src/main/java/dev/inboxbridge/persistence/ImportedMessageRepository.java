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

    public long countByDestinationKey(String destinationKey) {
        return count("destinationKey", destinationKey);
    }

    public List<Object[]> summarizeByImportedDay() {
        return getEntityManager().createNativeQuery(
                "select date_trunc('day', imported_at) as bucket_start, count(*) as imported_count "
                        + "from imported_message "
                        + "group by bucket_start "
                        + "order by bucket_start")
                .getResultList();
    }

    public List<Object[]> summarizeByImportedDayForDestinationKey(String destinationKey) {
        return getEntityManager().createNativeQuery(
                "select date_trunc('day', imported_at) as bucket_start, count(*) as imported_count "
                        + "from imported_message "
                        + "where destination_key = ?1 "
                        + "group by bucket_start "
                        + "order by bucket_start")
                .setParameter(1, destinationKey)
                .getResultList();
    }

    public List<Instant> listImportedAtSince(Instant since) {
        return find("select importedAt from ImportedMessage where importedAt >= ?1 order by importedAt", since)
                .project(Instant.class)
                .list();
    }

    public List<Instant> listImportedAtSinceForDestinationKey(String destinationKey, Instant since) {
        return find("select importedAt from ImportedMessage where destinationKey = ?1 and importedAt >= ?2 order by importedAt",
                destinationKey, since)
                .project(Instant.class)
                .list();
    }
}
