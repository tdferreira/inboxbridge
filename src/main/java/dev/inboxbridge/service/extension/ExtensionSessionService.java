package dev.inboxbridge.service.extension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.ExtensionSecurityConfig;
import dev.inboxbridge.dto.ExtensionSessionCreateRequest;
import dev.inboxbridge.dto.ExtensionSessionCreateView;
import dev.inboxbridge.dto.ExtensionSessionView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.ExtensionSessionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Mints, authenticates, rotates, lists, and revokes browser-extension tokens.
 * Existing manually created bearer tokens remain supported, while extension
 * login now uses short-lived access tokens plus refresh-token rotation.
 */
@ApplicationScoped
public class ExtensionSessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    ExtensionSessionRepository repository;

    @Inject
    ExtensionSecurityConfig extensionSecurityConfig;

    @Transactional
    public ExtensionSessionCreateView createSession(AppUser user, ExtensionSessionCreateRequest request) {
        String rawToken = generateRawToken();
        Instant now = Instant.now();

        ExtensionSession session = new ExtensionSession();
        session.userId = user.id;
        session.label = normalizeLabel(request == null ? null : request.label());
        session.browserFamily = normalizeBrowserFamily(request == null ? null : request.browserFamily());
        session.extensionVersion = normalizeExtensionVersion(request == null ? null : request.extensionVersion());
        session.tokenHash = hashToken(rawToken);
        session.tokenPrefix = tokenPrefix(rawToken);
        session.createdAt = now;

        repository.persist(session);

        return new ExtensionSessionCreateView(
                session.id,
                session.label,
                session.browserFamily,
                session.extensionVersion,
                rawToken,
                session.tokenPrefix,
                session.createdAt,
                session.lastUsedAt,
                session.expiresAt,
                session.revokedAt);
    }

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
        return new CreatedExtensionAuthSession(accessToken.rawToken(), refreshToken.rawToken(), session);
    }

    public List<ExtensionSessionView> listSessions(AppUser user) {
        return repository.listByUserId(user.id).stream()
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
        }
        return true;
    }

    @Transactional
    public Optional<AuthenticatedExtensionSession> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        return repository.findByTokenHash(hashToken(rawToken))
                .filter(session -> session.accessTokenActive(now))
                .map(session -> {
                    session.lastUsedAt = now;
                    return new AuthenticatedExtensionSession(session.id, session.userId, session.label);
                });
    }

    @Transactional
    public Optional<CreatedExtensionAuthSession> refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        return repository.findByRefreshTokenHash(hashToken(rawRefreshToken))
                .filter(session -> session.active(now))
                .map(session -> rotateSessionTokens(session, now));
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

    private CreatedExtensionAuthSession rotateSessionTokens(ExtensionSession session, Instant now) {
        TokenMaterial accessToken = generateToken();
        TokenMaterial refreshToken = generateToken();
        session.tokenHash = accessToken.tokenHash();
        session.tokenPrefix = accessToken.tokenPrefix();
        session.accessExpiresAt = now.plus(extensionSecurityConfig.accessTokenTtl());
        session.refreshTokenHash = refreshToken.tokenHash();
        session.lastUsedAt = now;
        session.expiresAt = now.plus(extensionSecurityConfig.refreshTokenTtl());
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
