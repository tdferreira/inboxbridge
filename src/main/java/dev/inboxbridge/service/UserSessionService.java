package dev.inboxbridge.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
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
    public CreatedUserSession createSession(AppUser user, String clientIp, String locationLabel, String userAgent, UserSession.LoginMethod loginMethod) {
        repository.deleteExpiredSessions();
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        byte[] csrfRaw = new byte[24];
        secureRandom.nextBytes(csrfRaw);
        String csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(csrfRaw);

        UserSession session = new UserSession();
        session.userId = user.id;
        session.tokenHash = sha256(token);
        session.csrfTokenHash = sha256(csrfToken);
        session.createdAt = Instant.now();
        session.lastSeenAt = session.createdAt;
        session.expiresAt = session.createdAt.plus(SESSION_TTL);
        session.clientIp = normalize(clientIp);
        session.locationLabel = normalize(locationLabel);
        session.userAgent = normalizeUserAgent(userAgent);
        session.loginMethod = loginMethod == null ? UserSession.LoginMethod.PASSWORD : loginMethod;
        repository.persist(session);
        return new CreatedUserSession(token, csrfToken, session);
    }

    @Transactional
    public Optional<UserSession> findValidSession(String token) {
        repository.deleteExpiredSessions();
        return repository.findByTokenHash(sha256(token))
                .filter(session -> session.revokedAt == null && Instant.now().isBefore(session.expiresAt))
                .map(session -> {
                    session.lastSeenAt = Instant.now();
                    return session;
                });
    }

    @Transactional
    public void invalidate(String token) {
        repository.findByTokenHash(sha256(token)).ifPresent(session -> session.revokedAt = Instant.now());
    }

    @Transactional
    public void invalidateUserSessions(Long userId) {
        repository.deleteByUserId(userId);
    }

    public List<UserSession> listRecentSessions(Long userId, int limit) {
        return repository.listRecentByUserId(userId, limit);
    }

    public List<UserSession> listActiveSessions(Long userId) {
        return repository.listActiveByUserId(userId, Instant.now());
    }

    @Transactional
    public void invalidateSessionForUser(Long userId, Long sessionId) {
        UserSession session = repository.findByIdOptional(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown session id"));
        if (!userId.equals(session.userId)) {
            throw new IllegalArgumentException("That session belongs to a different user.");
        }
        if (session.revokedAt == null) {
            session.revokedAt = Instant.now();
        }
    }

    @Transactional
    public void invalidateOtherSessions(Long userId, Long currentSessionId) {
        Instant now = Instant.now();
        repository.listActiveByUserId(userId, now).stream()
                .filter(session -> currentSessionId == null || !currentSessionId.equals(session.id))
                .forEach(session -> session.revokedAt = now);
    }

    @Transactional
    public void recordDeviceLocation(Long sessionId, Double latitude, Double longitude, Double accuracyMeters) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Missing current session");
        }
        UserSession session = repository.findByIdOptional(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown session id"));
        session.deviceLatitude = normalizeCoordinate(latitude, -90d, 90d, "latitude");
        session.deviceLongitude = normalizeCoordinate(longitude, -180d, 180d, "longitude");
        session.deviceAccuracyMeters = normalizeAccuracy(accuracyMeters);
        session.deviceLocationLabel = normalizeDeviceLocationLabel(SessionDeviceLocationFormatter.format(
                session.deviceLatitude,
                session.deviceLongitude,
                session.deviceAccuracyMeters));
        session.deviceLocationCapturedAt = Instant.now();
    }

    public boolean csrfMatches(UserSession session, String csrfToken) {
        return session != null
                && csrfToken != null
                && !csrfToken.isBlank()
                && session.csrfTokenHash != null
                && session.csrfTokenHash.equals(sha256(csrfToken));
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

    private Double normalizeCoordinate(Double value, double minimum, double maximum, String fieldName) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid " + fieldName);
        }
        return value;
    }

    private Double normalizeAccuracy(Double value) {
        if (value == null) {
            return null;
        }
        if (value.isNaN() || value.isInfinite() || value < 0d) {
            throw new IllegalArgumentException("Invalid accuracy");
        }
        return value;
    }

    private String normalizeDeviceLocationLabel(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > 160 ? normalized.substring(0, 160) : normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Session hashing failed", e);
        }
    }

    public record CreatedUserSession(String sessionToken, String csrfToken, UserSession session) {
    }
}
