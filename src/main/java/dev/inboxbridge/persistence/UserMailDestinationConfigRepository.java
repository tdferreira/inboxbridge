package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserMailDestinationConfigRepository implements PanacheRepository<UserMailDestinationConfig> {

    public Optional<UserMailDestinationConfig> findByUserId(Long userId) {
        return find("userId", userId).firstResultOptional();
    }

    public long deleteByUserId(Long userId) {
        return delete("userId", userId);
    }
}
