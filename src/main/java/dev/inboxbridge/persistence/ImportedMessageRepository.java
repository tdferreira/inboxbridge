package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ImportedMessageRepository implements PanacheRepository<ImportedMessage> {

    public boolean existsBySourceMessageKey(String destinationIdentityKey, String sourceAccountId, String sourceMessageKey) {
        return count("destinationIdentityKey = ?1 and sourceAccountId = ?2 and sourceMessageKey = ?3",
                destinationIdentityKey,
                sourceAccountId,
                sourceMessageKey) > 0;
    }

    public boolean existsByRawSha256(String destinationIdentityKey, String rawSha256) {
        return count("destinationIdentityKey = ?1 and rawSha256 = ?2", destinationIdentityKey, rawSha256) > 0;
    }

    public boolean existsByMessageIdHeader(String destinationIdentityKey, String sourceAccountId, String messageIdHeader) {
        return count("destinationIdentityKey = ?1 and sourceAccountId = ?2 and messageIdHeader = ?3",
                destinationIdentityKey,
                sourceAccountId,
                messageIdHeader) > 0;
    }

    public Optional<ImportedMessage> findBySourceMessageKey(String destinationIdentityKey, String sourceAccountId, String sourceMessageKey) {
        return find("destinationIdentityKey = ?1 and sourceAccountId = ?2 and sourceMessageKey = ?3",
                destinationIdentityKey,
                sourceAccountId,
                sourceMessageKey)
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

    public long countByDestinationKeyAndSourceAccountId(String destinationKey, String sourceAccountId) {
        return count("destinationKey = ?1 and sourceAccountId = ?2", destinationKey, sourceAccountId);
    }

    public List<Object[]> summarizeByImportedDayForDestinationKeyAndSourceAccountId(String destinationKey, String sourceAccountId) {
        return getEntityManager().createNativeQuery(
                "select date_trunc('day', imported_at) as bucket_start, count(*) as imported_count "
                        + "from imported_message "
                        + "where destination_key = ?1 and source_account_id = ?2 "
                        + "group by bucket_start "
                        + "order by bucket_start")
                .setParameter(1, destinationKey)
                .setParameter(2, sourceAccountId)
                .getResultList();
    }

    public List<Instant> listImportedAtSinceForDestinationKeyAndSourceAccountId(String destinationKey, String sourceAccountId, Instant since) {
        return find(
                "select importedAt from ImportedMessage where destinationKey = ?1 and sourceAccountId = ?2 and importedAt >= ?3 order by importedAt",
                destinationKey,
                sourceAccountId,
                since)
                .project(Instant.class)
                .list();
    }

    public long deleteBySourceAccountIds(List<String> sourceAccountIds) {
        if (sourceAccountIds == null || sourceAccountIds.isEmpty()) {
            return 0;
        }
        return delete("sourceAccountId in ?1", sourceAccountIds);
    }
}
