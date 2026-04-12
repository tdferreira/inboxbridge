package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExtensionSessionRepository implements PanacheRepository<ExtensionSession> {

    public Optional<ExtensionSession> findByTokenHash(String tokenHash) {
        return find("tokenHash", tokenHash).firstResultOptional();
    }

    public Optional<ExtensionSession> findByRefreshTokenHash(String refreshTokenHash) {
        return find("refreshTokenHash", refreshTokenHash).firstResultOptional();
    }

    public List<ExtensionSession> listByUserId(Long userId) {
        return list("userId = ?1 order by createdAt desc", userId);
    }

    public Optional<ExtensionSession> findByIdAndUserId(Long id, Long userId) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }

    public List<ExtensionSession> listActiveByUserId(Long userId, Instant now) {
        return list("userId = ?1 and revokedAt is null and (expiresAt is null or expiresAt > ?2) order by createdAt desc", userId, now);
    }
}
