package dev.inboxbridge.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.persistence.UserSessionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserSessionService {

    private static final Duration SESSION_TTL = Duration.ofDays(7);

    @Inject
    UserSessionRepository repository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String createSession(AppUser user) {
        repository.deleteExpiredSessions();
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        UserSession session = new UserSession();
        session.userId = user.id;
        session.tokenHash = sha256(token);
        session.createdAt = Instant.now();
        session.lastSeenAt = session.createdAt;
        session.expiresAt = session.createdAt.plus(SESSION_TTL);
        repository.persist(session);
        return token;
    }

    @Transactional
    public Optional<UserSession> findValidSession(String token) {
        repository.deleteExpiredSessions();
        return repository.findByTokenHash(sha256(token))
                .filter(session -> Instant.now().isBefore(session.expiresAt))
                .map(session -> {
                    session.lastSeenAt = Instant.now();
                    return session;
                });
    }

    @Transactional
    public void invalidate(String token) {
        repository.findByTokenHash(sha256(token)).ifPresent(repository::delete);
    }

    @Transactional
    public void invalidateUserSessions(Long userId) {
        repository.deleteByUserId(userId);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Session hashing failed", e);
        }
    }
}
