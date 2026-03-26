package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserSessionRepository implements PanacheRepository<UserSession> {

    public Optional<UserSession> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    @Transactional
    public void deleteExpiredSessions() {
        delete("expiresAt < ?1", Instant.now());
    }

    @Transactional
    public void deleteByUserId(Long userId) {
        delete("userId", userId);
    }
}
