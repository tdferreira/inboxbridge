package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserSessionRepository implements PanacheRepository<UserSession> {

    public Optional<UserSession> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    public List<UserSession> listRecentByUserId(Long userId, int limit) {
        return find("userId = ?1 order by createdAt desc", userId).page(0, limit).list();
    }

    public List<UserSession> listActiveByUserId(Long userId, Instant now) {
        return find("userId = ?1 and revokedAt is null and expiresAt > ?2 order by lastSeenAt desc", userId, now).list();
    }

    public void deleteExpiredSessions() {
        // Preserve session history for the security panel; expired sessions are
        // excluded from active-session queries rather than deleted eagerly.
    }

    public void deleteByUserId(Long userId) {
        update("revokedAt = ?1 where userId = ?2 and revokedAt is null", Instant.now(), userId);
    }
}
