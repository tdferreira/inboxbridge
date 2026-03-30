package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class PollThrottleStateRepository implements PanacheRepository<PollThrottleState> {

    public Optional<PollThrottleState> findByThrottleKey(String throttleKey) {
        return find("throttleKey", throttleKey).firstResultOptional();
    }

    public Optional<PollThrottleState> findByThrottleKeyForUpdate(String throttleKey) {
        return find("throttleKey", throttleKey)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
