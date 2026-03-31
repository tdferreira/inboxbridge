package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RemoteSessionRepository implements PanacheRepository<RemoteSession> {

    public Optional<RemoteSession> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    public List<RemoteSession> listRecentByUserId(Long userId, int limit) {
        return find("userId = ?1 order by createdAt desc", userId).page(0, limit).list();
    }

    public List<RemoteSession> listActiveByUserId(Long userId, Instant now) {
        return find("userId = ?1 and revokedAt is null and expiresAt > ?2 order by lastSeenAt desc", userId, now).list();
    }

    public Optional<RemoteSession> findByIdForUser(Long sessionId, Long userId) {
        return find("id = ?1 and userId = ?2", sessionId, userId).firstResultOptional();
    }

    @Transactional
    public void revokeByUserId(Long userId) {
        update("revokedAt = ?1 where userId = ?2 and revokedAt is null", Instant.now(), userId);
    }

    @Transactional
    public void revokeOtherByUserId(Long userId, Instant revokedAt) {
        update("revokedAt = ?1 where userId = ?2 and revokedAt is null", revokedAt, userId);
    }
}
