package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PasskeyCeremonyRepository implements PanacheRepository<PasskeyCeremony> {

    public Optional<PasskeyCeremony> findValid(String id, PasskeyCeremony.CeremonyType ceremonyType, Instant now) {
        return find("id = ?1 and ceremonyType = ?2 and expiresAt > ?3", id, ceremonyType, now).firstResultOptional();
    }

    @Transactional
    public long deleteExpired(Instant now) {
        return delete("expiresAt <= ?1", now);
    }
}
