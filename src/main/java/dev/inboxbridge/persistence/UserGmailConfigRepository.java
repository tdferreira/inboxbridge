package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserGmailConfigRepository implements PanacheRepository<UserGmailConfig> {

    public Optional<UserGmailConfig> findByUserId(Long userId) {
        return find("userId", userId).firstResultOptional();
    }
}
