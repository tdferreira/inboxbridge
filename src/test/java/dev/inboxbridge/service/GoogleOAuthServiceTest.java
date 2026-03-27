package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

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
                return false;
            }
        };

        GoogleTokenExchangeResponse response = service.exchangeAuthorizationCode(
                new GoogleOAuthService.GoogleOAuthProfile(
                        "gmail-destination",
                        "client-id",
                        "client-secret",
                        "previous-refresh",
                        "https://localhost:3000/api/google-oauth/callback"),
                "auth-code");

        assertTrue(response.sameLinkedAccount());
        assertFalse(response.replacedExistingAccount());
        assertTrue(response.previousGrantRevoked());
        assertFalse(service.revokeCalled);
    }

    private static final class FakeGoogleOAuthService extends GoogleOAuthService {
        private boolean revokeCalled;

        @Override
        protected GoogleTokenResponse executeTokenRequest(String body) {
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
