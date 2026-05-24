package dev.inboxbridge.service.extension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.ExtensionSecurityConfig;
import dev.inboxbridge.dto.ExtensionSessionView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.ExtensionSessionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Mints, authenticates, rotates, lists, and revokes browser-extension tokens
 * issued through the direct browser-extension sign-in flow.
 */
@ApplicationScoped
public class ExtensionSessionService {

    private static final Logger LOG = Logger.getLogger(ExtensionSessionService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    static final Duration REVOKED_SESSION_VISIBLE_HISTORY = Duration.ofDays(30);

    @Inject
    ExtensionSessionRepository repository;

    @Inject
    ExtensionSecurityConfig extensionSecurityConfig;

    @Transactional
    public CreatedExtensionAuthSession createAuthenticatedSession(
            AppUser user,
            String label,
            String browserFamily,
            String extensionVersion) {
        Instant now = Instant.now();
        TokenMaterial accessToken = generateToken();
        TokenMaterial refreshToken = generateToken();

        ExtensionSession session = new ExtensionSession();
        session.userId = user.id;
        session.label = normalizeLabel(label);
        session.browserFamily = normalizeBrowserFamily(browserFamily);
        session.extensionVersion = normalizeExtensionVersion(extensionVersion);
        session.tokenHash = accessToken.tokenHash();
        session.tokenPrefix = accessToken.tokenPrefix();
        session.accessExpiresAt = now.plus(extensionSecurityConfig.accessTokenTtl());
        session.refreshTokenHash = refreshToken.tokenHash();
        session.createdAt = now;
        session.lastUsedAt = now;
        session.expiresAt = now.plus(extensionSecurityConfig.refreshTokenTtl());

        repository.persist(session);
        LOG.infof(
                "Created browser-extension session id=%s userId=%s browser=%s version=%s accessExpiresAt=%s refreshExpiresAt=%s tokenFingerprint=%s",
                session.id,
                session.userId,
                session.browserFamily,
                session.extensionVersion,
                session.accessExpiresAt,
                session.expiresAt,
                tokenFingerprint(accessToken.rawToken()));
        return new CreatedExtensionAuthSession(accessToken.rawToken(), refreshToken.rawToken(), session);
    }

    public List<ExtensionSessionView> listSessions(AppUser user) {
        Instant revokedAfter = Instant.now().minus(REVOKED_SESSION_VISIBLE_HISTORY);
        return repository.listVisibleByUserId(user.id, revokedAfter).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public boolean revokeSession(AppUser user, Long sessionId) {
        Optional<ExtensionSession> session = repository.findByIdAndUserId(sessionId, user.id);
        if (session.isEmpty()) {
            return false;
        }
        if (session.get().revokedAt == null) {
            session.get().revokedAt = Instant.now();
            LOG.infof("Revoked browser-extension session id=%s userId=%s", session.get().id, user.id);
        }
        return true;
    }

    @Transactional
    public List<Long> revokeAllSessions(AppUser user) {
        Instant now = Instant.now();
        List<ExtensionSession> unrevokedSessions = repository.listUnrevokedByUserId(user.id);
        unrevokedSessions.forEach((session) -> session.revokedAt = now);
        if (!unrevokedSessions.isEmpty()) {
            LOG.infof("Revoked %d browser-extension session(s) for userId=%s", unrevokedSessions.size(), user.id);
        }
        return unrevokedSessions.stream()
                .map(session -> session.id)
                .toList();
    }

    @Transactional
    public int revokeAllSessionsForAllUsers() {
        Instant now = Instant.now();
        List<ExtensionSession> unrevokedSessions = repository.listAllUnrevoked();
        unrevokedSessions.forEach((session) -> session.revokedAt = now);
        if (!unrevokedSessions.isEmpty()) {
            LOG.infof("Revoked %d browser-extension session(s) across all users", unrevokedSessions.size());
        }
        return unrevokedSessions.size();
    }

    @Transactional
    public Optional<AuthenticatedExtensionSession> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            LOG.warn("Rejected browser-extension access token authentication because the bearer token was empty");
            return Optional.empty();
        }

        Instant now = Instant.now();
        String tokenHash = hashToken(rawToken);
        String fingerprint = tokenFingerprint(rawToken);
        Optional<ExtensionSession> found = repository.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            LOG.warnf("Rejected browser-extension access token authentication because no session matched tokenFingerprint=%s", fingerprint);
            return Optional.empty();
        }
        ExtensionSession session = found.get();
        if (!session.accessTokenActive(now)) {
            LOG.warnf(
                    "Rejected browser-extension access token authentication for session id=%s userId=%s reason=%s accessExpiresAt=%s refreshExpiresAt=%s revokedAt=%s tokenFingerprint=%s",
                    session.id,
                    session.userId,
                    inactiveReason(session, now, true),
                    session.accessExpiresAt,
                    session.expiresAt,
                    session.revokedAt,
                    fingerprint);
            return Optional.empty();
        }
        return Optional.of(session)
                .map(activeSession -> {
                    activeSession.lastUsedAt = now;
                    return new AuthenticatedExtensionSession(activeSession.id, activeSession.userId, activeSession.label);
                });
    }

    @Transactional
    public Optional<CreatedExtensionAuthSession> refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            LOG.warn("Rejected browser-extension refresh because the refresh token was empty");
            return Optional.empty();
        }

        Instant now = Instant.now();
        String refreshHash = hashToken(rawRefreshToken);
        String fingerprint = tokenFingerprint(rawRefreshToken);
        Optional<ExtensionSession> found = repository.findByRefreshTokenHashForUpdate(refreshHash);
        if (found.isEmpty()) {
            LOG.warnf("Rejected browser-extension refresh because no session matched refreshFingerprint=%s", fingerprint);
            return Optional.empty();
        }
        ExtensionSession session = found.get();
        if (!session.active(now)) {
            LOG.warnf(
                    "Rejected browser-extension refresh for session id=%s userId=%s reason=%s refreshExpiresAt=%s revokedAt=%s refreshFingerprint=%s",
                    session.id,
                    session.userId,
                    inactiveReason(session, now, false),
                    session.expiresAt,
                    session.revokedAt,
                    fingerprint);
            return Optional.empty();
        }
        return Optional.of(rotateSessionTokens(session, now, fingerprint));
    }

    /**
     * Produces the stable persisted token hash used for bearer-token lookup.
     */
    String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash extension token", e);
        }
    }

    private CreatedExtensionAuthSession rotateSessionTokens(ExtensionSession session, Instant now, String previousRefreshFingerprint) {
        TokenMaterial accessToken = generateToken();
        TokenMaterial refreshToken = generateToken();
        session.tokenHash = accessToken.tokenHash();
        session.tokenPrefix = accessToken.tokenPrefix();
        session.accessExpiresAt = now.plus(extensionSecurityConfig.accessTokenTtl());
        session.refreshTokenHash = refreshToken.tokenHash();
        session.lastUsedAt = now;
        session.expiresAt = now.plus(extensionSecurityConfig.refreshTokenTtl());
        LOG.infof(
                "Rotated browser-extension tokens for session id=%s userId=%s browser=%s version=%s previousRefreshFingerprint=%s newAccessExpiresAt=%s newRefreshExpiresAt=%s newAccessFingerprint=%s",
                session.id,
                session.userId,
                session.browserFamily,
                session.extensionVersion,
                previousRefreshFingerprint,
                session.accessExpiresAt,
                session.expiresAt,
                tokenFingerprint(accessToken.rawToken()));
        return new CreatedExtensionAuthSession(accessToken.rawToken(), refreshToken.rawToken(), session);
    }

    private TokenMaterial generateToken() {
        String rawToken = generateRawToken();
        return new TokenMaterial(rawToken, hashToken(rawToken), tokenPrefix(rawToken));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "ibx_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String tokenPrefix(String rawToken) {
        return rawToken.length() <= 12 ? rawToken : rawToken.substring(0, 12);
    }

    private String tokenFingerprint(String rawToken) {
        String hash = hashToken(rawToken);
        return hash.length() <= 16 ? hash : hash.substring(0, 16);
    }

    private String inactiveReason(ExtensionSession session, Instant now, boolean includeAccessToken) {
        if (session.revokedAt != null) {
            return "revoked";
        }
        if (session.expiresAt != null && !session.expiresAt.isAfter(now)) {
            return "refresh_expired";
        }
        if (includeAccessToken && session.accessExpiresAt != null && !session.accessExpiresAt.isAfter(now)) {
            return "access_expired";
        }
        return "inactive";
    }

    private ExtensionSessionView toView(ExtensionSession session) {
        return new ExtensionSessionView(
                session.id,
                session.label,
                session.browserFamily,
                session.extensionVersion,
                session.tokenPrefix,
                session.createdAt,
                session.lastUsedAt,
                session.expiresAt,
                session.revokedAt);
    }

    String normalizeLabel(String value) {
        if (value == null || value.isBlank()) {
            return "Browser extension";
        }
        return truncate(value.trim(), 120);
    }

    String normalizeBrowserFamily(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return truncate(value.trim().toLowerCase(), 32);
    }

    String normalizeExtensionVersion(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return truncate(value.trim(), 32);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record AuthenticatedExtensionSession(
            Long sessionId,
            Long userId,
            String label) {
    }

    public record CreatedExtensionAuthSession(
            String accessToken,
            String refreshToken,
            ExtensionSession session) {
    }

    private record TokenMaterial(
            String rawToken,
            String tokenHash,
            String tokenPrefix) {
    }
}
