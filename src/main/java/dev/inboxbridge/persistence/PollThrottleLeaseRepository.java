package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PollThrottleLeaseRepository implements PanacheRepository<PollThrottleLease> {

    @Transactional
    public long deleteExpired(String throttleKey, Instant now) {
        return delete("throttleKey = ?1 and expiresAt <= ?2", throttleKey, now);
    }

    public long countActive(String throttleKey, Instant now) {
        return count("throttleKey = ?1 and expiresAt > ?2", throttleKey, now);
    }

    public Optional<Instant> earliestActiveExpiry(String throttleKey, Instant now) {
        return find("throttleKey = ?1 and expiresAt > ?2 order by expiresAt", throttleKey, now)
                .firstResultOptional()
                .map(lease -> lease.expiresAt);
    }

    @Transactional
    public long deleteByLeaseToken(String leaseToken) {
        return delete("leaseToken", leaseToken);
    }
}
