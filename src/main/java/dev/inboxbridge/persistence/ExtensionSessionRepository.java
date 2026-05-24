package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class ExtensionSessionRepository implements PanacheRepository<ExtensionSession> {

    public Optional<ExtensionSession> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    public Optional<ExtensionSession> findByRefreshTokenHash(String refreshTokenHash) {
        return find("refreshTokenHash", refreshTokenHash).firstResultOptional();
    }

    public Optional<ExtensionSession> findByRefreshTokenHashForUpdate(String refreshTokenHash) {
        return find("refreshTokenHash", refreshTokenHash)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public List<ExtensionSession> listByUserId(Long userId) {
        return list("userId = ?1 order by createdAt desc", userId);
    }

    public List<ExtensionSession> listVisibleByUserId(Long userId, Instant revokedAfter) {
        return list(
                "userId = ?1 and (revokedAt is null or revokedAt >= ?2) order by createdAt desc",
                userId,
                revokedAfter);
    }

    public Optional<ExtensionSession> findByIdAndUserId(Long id, Long userId) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }

    public List<ExtensionSession> listActiveByUserId(Long userId, Instant now) {
        return list("userId = ?1 and revokedAt is null and (expiresAt is null or expiresAt > ?2) order by createdAt desc", userId, now);
    }

    public List<ExtensionSession> listAllActive(Instant now) {
        return list("revokedAt is null and (expiresAt is null or expiresAt > ?1) order by createdAt desc", now);
    }
}
