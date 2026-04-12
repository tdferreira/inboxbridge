package dev.inboxbridge.service.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ExtensionSession;

class ExtensionBrowserAuthHandoffServiceTest {

    @Test
    void redeemReturnsPendingUntilBrowserSessionCompletes() {
        ExtensionBrowserAuthHandoffService service = configuredService();
        String verifier = "browser-verifier";
        var started = service.start(s256(verifier), "S256", "Laptop", "firefox", "0.1.0");

        var result = service.redeem(started.requestId(), verifier);

        assertEquals(ExtensionBrowserAuthHandoffService.Status.PENDING, result.status());
        assertEquals(Instant.parse("2026-04-12T10:05:00Z"), result.expiresAt());
    }

    @Test
    void redeemCreatesExtensionSessionAfterBrowserCompletion() {
        ExtensionBrowserAuthHandoffService service = configuredService();
        String verifier = "browser-verifier";
        var started = service.start(s256(verifier), "S256", "Laptop", "firefox", "0.1.0");
        AppUser user = new AppUser();
        user.id = 9L;

        service.complete(started.requestId(), user);
        var result = service.redeem(started.requestId(), verifier);

        assertEquals(ExtensionBrowserAuthHandoffService.Status.AUTHENTICATED, result.status());
        assertNotNull(result.session());
        assertEquals("access-1", result.session().accessToken());
        assertEquals("refresh-1", result.session().refreshToken());
        assertEquals(9L, result.session().session().userId);
        assertEquals("Laptop", result.session().session().label);
    }

    @Test
    void completeStillSucceedsAfterRedeemConsumesTheExtensionSession() {
        ExtensionBrowserAuthHandoffService service = configuredService();
        String verifier = "browser-verifier";
        var started = service.start(s256(verifier), "S256", "Laptop", "firefox", "0.1.0");
        AppUser user = new AppUser();
        user.id = 9L;

        service.complete(started.requestId(), user);
        var result = service.redeem(started.requestId(), verifier);

        assertEquals(ExtensionBrowserAuthHandoffService.Status.AUTHENTICATED, result.status());
        assertDoesNotThrow(() -> service.complete(started.requestId(), user));
    }

    @Test
    void redeemRejectsWrongVerifier() {
        ExtensionBrowserAuthHandoffService service = configuredService();
        var started = service.start(s256("browser-verifier"), "S256", "Laptop", "firefox", "0.1.0");
        AppUser user = new AppUser();
        user.id = 9L;
        service.complete(started.requestId(), user);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.redeem(started.requestId(), "wrong"));

        assertEquals("The browser extension sign-in verification failed.", error.getMessage());
    }

    @Test
    void redeemExpiresAfterTtl() {
        ExtensionBrowserAuthHandoffService service = configuredService();
        String verifier = "browser-verifier";
        var started = service.start(s256(verifier), "S256", "Laptop", "firefox", "0.1.0");

        service.clock = Clock.fixed(Instant.parse("2026-04-12T10:05:00Z"), ZoneOffset.UTC);
        var result = service.redeem(started.requestId(), verifier);

        assertEquals(ExtensionBrowserAuthHandoffService.Status.EXPIRED, result.status());
    }

    private static ExtensionBrowserAuthHandoffService configuredService() {
        ExtensionBrowserAuthHandoffService service = new ExtensionBrowserAuthHandoffService();
        service.clock = Clock.fixed(Instant.parse("2026-04-12T10:00:00Z"), ZoneOffset.UTC);
        service.extensionSessionService = new ExtensionSessionService() {
            @Override
            public CreatedExtensionAuthSession createAuthenticatedSession(AppUser user, String label, String browserFamily, String extensionVersion) {
                ExtensionSession session = new ExtensionSession();
                session.id = 21L;
                session.userId = user.id;
                session.label = label;
                session.browserFamily = browserFamily;
                session.extensionVersion = extensionVersion;
                return new CreatedExtensionAuthSession("access-1", "refresh-1", session);
            }
        };
        return service;
    }

    private static String s256(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
