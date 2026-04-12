package dev.inboxbridge.service.extension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.inboxbridge.persistence.AppUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Coordinates short-lived browser-session handoffs so extension sign-in can
 * complete on the normal InboxBridge web origin before exchanging for
 * extension-scoped rotating tokens.
 */
@ApplicationScoped
public class ExtensionBrowserAuthHandoffService {

    static final Duration HANDOFF_TTL = Duration.ofMinutes(5);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Inject
    ExtensionSessionService extensionSessionService;

    Clock clock = Clock.systemUTC();

    private final Map<String, PendingBrowserAuthHandoff> handoffs = new ConcurrentHashMap<>();

    public StartedBrowserAuthHandoff start(
            String codeChallenge,
            String codeChallengeMethod,
            String label,
            String browserFamily,
            String extensionVersion) {
        pruneExpired();
        if (codeChallenge == null || codeChallenge.isBlank()) {
            throw new IllegalArgumentException("A browser extension sign-in challenge is required.");
        }
        if (!"S256".equalsIgnoreCase(String.valueOf(codeChallengeMethod))) {
            throw new IllegalArgumentException("InboxBridge only supports S256 extension sign-in challenges.");
        }
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(HANDOFF_TTL);
        PendingBrowserAuthHandoff handoff = new PendingBrowserAuthHandoff(
                randomToken(24),
                codeChallenge.trim(),
                extensionSessionService.normalizeLabel(label),
                extensionSessionService.normalizeBrowserFamily(browserFamily),
                extensionSessionService.normalizeExtensionVersion(extensionVersion),
                now,
                expiresAt);
        handoffs.put(handoff.requestId(), handoff);
        return new StartedBrowserAuthHandoff(handoff.requestId(), handoff.expiresAt());
    }

    public boolean complete(String requestId, AppUser user) {
        pruneExpired();
        PendingBrowserAuthHandoff handoff = activeHandoff(requestId)
                .orElseThrow(() -> new IllegalArgumentException("The browser extension sign-in request is invalid or expired."));
        if (user == null || user.id == null) {
            throw new IllegalArgumentException("The browser sign-in session is not available.");
        }
        handoff.completedUserId = user.id;
        handoff.completedAt = Instant.now(clock);
        return true;
    }

    public BrowserAuthRedeemResult redeem(String requestId, String codeVerifier) {
        pruneExpired();
        PendingBrowserAuthHandoff handoff = handoffs.get(normalizeRequestId(requestId));
        if (handoff == null) {
            return BrowserAuthRedeemResult.expired();
        }
        Instant now = Instant.now(clock);
        if (handoff.expiresAt().isBefore(now) || handoff.expiresAt().equals(now)) {
            handoffs.remove(handoff.requestId());
            return BrowserAuthRedeemResult.expired();
        }
        if (handoff.completedUserId == null) {
            return BrowserAuthRedeemResult.pending(handoff.expiresAt());
        }
        if (handoff.redeemedAt != null) {
            return BrowserAuthRedeemResult.expired();
        }
        if (codeVerifier == null || codeVerifier.isBlank() || !handoff.codeChallenge().equals(s256(codeVerifier.trim()))) {
            throw new IllegalArgumentException("The browser extension sign-in verification failed.");
        }

        AppUser user = new AppUser();
        user.id = handoff.completedUserId;
        handoff.redeemedAt = now;
        return BrowserAuthRedeemResult.authenticated(
                extensionSessionService.createAuthenticatedSession(
                        user,
                        handoff.label(),
                        handoff.browserFamily(),
                        handoff.extensionVersion()),
                handoff.expiresAt());
    }

    private Optional<PendingBrowserAuthHandoff> activeHandoff(String requestId) {
        return Optional.ofNullable(handoffs.get(normalizeRequestId(requestId)))
                .filter(handoff -> handoff.expiresAt().isAfter(Instant.now(clock)));
    }

    private void pruneExpired() {
        Instant now = Instant.now(clock);
        handoffs.values().removeIf(handoff -> !handoff.expiresAt().isAfter(now));
    }

    private String normalizeRequestId(String requestId) {
        return String.valueOf(requestId == null ? "" : requestId).trim();
    }

    private String randomToken(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String s256(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Could not verify the extension browser sign-in challenge.", e);
        }
    }

    public record StartedBrowserAuthHandoff(
            String requestId,
            Instant expiresAt) {
    }

    public record BrowserAuthRedeemResult(
            Status status,
            Instant expiresAt,
            ExtensionSessionService.CreatedExtensionAuthSession session) {

        public static BrowserAuthRedeemResult pending(Instant expiresAt) {
            return new BrowserAuthRedeemResult(Status.PENDING, expiresAt, null);
        }

        public static BrowserAuthRedeemResult authenticated(
                ExtensionSessionService.CreatedExtensionAuthSession session,
                Instant expiresAt) {
            return new BrowserAuthRedeemResult(Status.AUTHENTICATED, expiresAt, session);
        }

        public static BrowserAuthRedeemResult expired() {
            return new BrowserAuthRedeemResult(Status.EXPIRED, null, null);
        }
    }

    public enum Status {
        PENDING,
        AUTHENTICATED,
        EXPIRED
    }

    private static final class PendingBrowserAuthHandoff {
        private final String requestId;
        private final String codeChallenge;
        private final String label;
        private final String browserFamily;
        private final String extensionVersion;
        private final Instant createdAt;
        private final Instant expiresAt;
        private Long completedUserId;
        private Instant completedAt;
        private Instant redeemedAt;

        private PendingBrowserAuthHandoff(
                String requestId,
                String codeChallenge,
                String label,
                String browserFamily,
                String extensionVersion,
                Instant createdAt,
                Instant expiresAt) {
            this.requestId = requestId;
            this.codeChallenge = codeChallenge;
            this.label = label;
            this.browserFamily = browserFamily;
            this.extensionVersion = extensionVersion;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        private String requestId() {
            return requestId;
        }

        private String codeChallenge() {
            return codeChallenge;
        }

        private String label() {
            return label;
        }

        private String browserFamily() {
            return browserFamily;
        }

        private String extensionVersion() {
            return extensionVersion;
        }

        private Instant createdAt() {
            return createdAt;
        }

        private Instant expiresAt() {
            return expiresAt;
        }
    }
}
