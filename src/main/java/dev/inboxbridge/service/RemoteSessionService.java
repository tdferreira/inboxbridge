package dev.inboxbridge.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.persistence.RemoteSessionRepository;
import dev.inboxbridge.persistence.UserSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RemoteSessionService {

    @Inject
    RemoteSessionRepository repository;

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public CreatedRemoteSession createSession(
            AppUser user,
            String clientIp,
            String locationLabel,
            String userAgent,
            UserSession.LoginMethod loginMethod) {
        byte[] sessionBytes = new byte[32];
        byte[] csrfBytes = new byte[24];
        secureRandom.nextBytes(sessionBytes);
        secureRandom.nextBytes(csrfBytes);

        String sessionToken = Base64.getUrlEncoder().withoutPadding().encodeToString(sessionBytes);
        String csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(csrfBytes);

        RemoteSession session = new RemoteSession();
        session.userId = user.id;
        session.tokenHash = sha256(sessionToken);
        session.csrfTokenHash = sha256(csrfToken);
        session.createdAt = Instant.now();
        session.lastSeenAt = session.createdAt;
        session.expiresAt = session.createdAt.plus(inboxBridgeConfig.security().remote().sessionTtl());
        session.clientIp = normalize(clientIp);
        session.locationLabel = normalize(locationLabel);
        session.userAgent = normalizeUserAgent(userAgent);
        session.loginMethod = loginMethod == null ? UserSession.LoginMethod.PASSWORD : loginMethod;
        repository.persist(session);
        return new CreatedRemoteSession(sessionToken, csrfToken, session);
    }

    @Transactional
    public Optional<RemoteSession> findValidSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(sha256(token))
                .filter(session -> session.revokedAt == null && Instant.now().isBefore(session.expiresAt))
                .map(session -> {
                    session.lastSeenAt = Instant.now();
                    return session;
                });
    }

    public boolean csrfMatches(RemoteSession session, String csrfToken) {
        return session != null
                && csrfToken != null
                && !csrfToken.isBlank()
                && session.csrfTokenHash.equals(sha256(csrfToken));
    }

    @Transactional
    public void invalidate(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        repository.findByTokenHash(sha256(token)).ifPresent(session -> session.revokedAt = Instant.now());
    }

    public List<RemoteSession> listRecentSessions(Long userId, int limit) {
        return repository.listRecentByUserId(userId, limit);
    }

    public List<RemoteSession> listActiveSessions(Long userId) {
        return repository.listActiveByUserId(userId, Instant.now());
    }

    @Transactional
    public void invalidateSessionForUser(Long userId, Long sessionId) {
        RemoteSession session = repository.findByIdForUser(sessionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown session id"));
        if (session.revokedAt == null) {
            session.revokedAt = Instant.now();
        }
    }

    @Transactional
    public void invalidateOtherSessions(Long userId) {
        repository.revokeOtherByUserId(userId, Instant.now());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeUserAgent(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 512 ? normalized.substring(0, 512) : normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Remote session hashing failed", e);
        }
    }

    public record CreatedRemoteSession(String sessionToken, String csrfToken, RemoteSession session) {
    }
}
