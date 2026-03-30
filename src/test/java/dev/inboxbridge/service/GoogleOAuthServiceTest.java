package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.GoogleTokenExchangeResponse;
import dev.inboxbridge.dto.GoogleTokenResponse;

class GoogleOAuthServiceTest {

    @Test
    void sameGmailAccountReauthorizationDoesNotRevokePreviousGrant() {
        FakeGoogleOAuthService service = new FakeGoogleOAuthService();
        service.oAuthCredentialService = new OAuthCredentialService() {
            @Override
            public boolean secureStorageConfigured() {
                return true;
            }

            @Override
            public Optional<StoredOAuthCredential> findGoogleCredential(String subjectKey) {
                return Optional.of(new StoredOAuthCredential(
                        GOOGLE_PROVIDER,
                        subjectKey,
                        "previous-refresh",
                        "previous-access-token",
                        Instant.parse("2026-03-30T12:00:00Z"),
                        GoogleOAuthService.GMAIL_TARGET_SCOPE,
                        "Bearer",
                        Instant.parse("2026-03-30T11:55:00Z")));
            }

            @Override
            public StoredOAuthCredential storeGoogleCredential(String subjectKey, String refreshToken, String accessToken, Instant accessExpiresAt, String scope, String tokenType) {
                return new StoredOAuthCredential(GOOGLE_PROVIDER, subjectKey, refreshToken, accessToken, accessExpiresAt, scope, tokenType, Instant.now());
            }
        };

        GoogleTokenExchangeResponse response = service.exchangeAuthorizationCode(
                new GoogleOAuthService.GoogleOAuthProfile(
                        "gmail-destination",
                        "client-id",
                        "client-secret",
                        "previous-refresh",
                        "https://localhost:3000/api/google-oauth/callback",
                        GoogleOAuthService.GMAIL_TARGET_SCOPE),
                "auth-code");

        assertTrue(response.sameLinkedAccount());
        assertFalse(response.replacedExistingAccount());
        assertTrue(response.previousGrantRevoked());
        assertFalse(service.revokeCalled);
    }

    @Test
    void exchangeAuthorizationCodeRequiresSecureStorage() {
        FakeGoogleOAuthService service = new FakeGoogleOAuthService();
        service.oAuthCredentialService = new OAuthCredentialService() {
            @Override
            public boolean secureStorageConfigured() {
                return false;
            }
        };

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.exchangeAuthorizationCode(
                        new GoogleOAuthService.GoogleOAuthProfile(
                                "gmail-destination",
                                "client-id",
                                "client-secret",
                                "previous-refresh",
                                "https://localhost:3000/api/google-oauth/callback",
                                GoogleOAuthService.GMAIL_TARGET_SCOPE),
                        "auth-code"));

        assertEquals(
            "Secure token storage is required before completing Google OAuth. Set SECURITY_TOKEN_ENCRYPTION_KEY to a base64-encoded 32-byte key, restart InboxBridge, and then retry the OAuth flow.",
                error.getMessage());
    }

    private static final class FakeGoogleOAuthService extends GoogleOAuthService {
        private boolean revokeCalled;

        @Override
        protected GoogleTokenResponse executeTokenRequest(GoogleOAuthProfile profile, String body, boolean refreshFlow) {
            return new GoogleTokenResponse(
                    "new-access-token",
                    "new-refresh-token",
                    300L,
                    "https://www.googleapis.com/auth/gmail.insert https://www.googleapis.com/auth/gmail.labels",
                    "Bearer");
        }

        @Override
        protected String resolveAccountAddress(GoogleOAuthProfile profile) {
            return "alice@example.com";
        }

        @Override
        protected String resolveAccountAddress(String accessToken) {
            return "alice@example.com";
        }

        @Override
        public boolean revokeToken(String token) {
            revokeCalled = true;
            return true;
        }

        @Override
        public String getAccessToken(GoogleOAuthProfile profile) {
            return "previous-access-token";
        }
    }
}
