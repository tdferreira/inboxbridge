package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class PasskeyCeremonyRepository implements PanacheRepository<PasskeyCeremony> {

    public Optional<PasskeyCeremony> findValid(String id, PasskeyCeremony.CeremonyType ceremonyType, Instant now) {
        // Keep verification and deletion in one transaction so an assertion cannot
        // authenticate twice through concurrent requests or separate app instances.
        return find("id = ?1 and ceremonyType = ?2 and expiresAt > ?3", id, ceremonyType, now)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    @Transactional
    public long deleteExpired(Instant now) {
        return delete("expiresAt <= ?1", now);
    }
}
