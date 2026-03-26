package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.dto.UpdateUserGmailConfigRequest;
import dev.inboxbridge.dto.UserGmailConfigView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;

class UserGmailConfigServiceTest {

    @Test
    void updateAllowsNonSecretFieldsWithoutEncryption() {
        UserGmailConfigService service = service(false, repositoryWithNoRow(), Optional.empty());
        AppUser user = new AppUser();
        user.id = 7L;

        UserGmailConfigView view = service.update(user, new UpdateUserGmailConfigRequest(
                "me",
                "",
                "",
                "",
                "",
                true,
                false,
                false));

        assertEquals("me", view.destinationUser());
        assertEquals("https://mail.example.test/api/google-oauth/callback", view.redirectUri());
        assertTrue(view.clientIdConfigured());
        assertTrue(view.clientSecretConfigured());
        assertTrue(view.sharedClientConfigured());
    }

    @Test
    void googleProfileFallsBackToSharedClientWhenUserSpecificSecretsMissing() {
        InMemoryUserGmailConfigRepository repository = repositoryWithNoRow();
        UserGmailConfig stored = new UserGmailConfig();
        stored.userId = 9L;
        stored.destinationUser = "me";
        stored.redirectUri = "https://custom.example.test/api/google-oauth/callback";
        repository.persist(stored);

        UserGmailConfigService service = service(false, repository, Optional.empty());

        GoogleOAuthService.GoogleOAuthProfile profile = service.googleProfileForUser(9L).orElseThrow();

        assertEquals("user-gmail:9", profile.subjectKey());
        assertEquals("shared-client-id", profile.clientId());
        assertEquals("shared-client-secret", profile.clientSecret());
        assertEquals("", profile.refreshToken());
        assertEquals("https://custom.example.test/api/google-oauth/callback", profile.redirectUri());
    }

    @Test
    void defaultViewIncludesSharedClientAndDefaultRedirectUri() {
        UserGmailConfigService service = service(false, repositoryWithNoRow(), Optional.empty());

        UserGmailConfigView view = service.defaultView(7L);

        assertTrue(view.sharedClientConfigured());
        assertTrue(view.clientIdConfigured());
        assertTrue(view.clientSecretConfigured());
        assertEquals("https://mail.example.test/api/google-oauth/callback", view.defaultRedirectUri());
        assertEquals("https://mail.example.test/api/google-oauth/callback", view.redirectUri());
    }

    @Test
    void viewReflectsStoredGoogleRefreshTokenFromOAuthCredentialTable() {
        InMemoryUserGmailConfigRepository repository = repositoryWithNoRow();
        UserGmailConfig stored = new UserGmailConfig();
        stored.userId = 11L;
        stored.destinationUser = "me";
        stored.redirectUri = "https://mail.example.test/api/google-oauth/callback";
        repository.persist(stored);

        OAuthCredentialService.StoredOAuthCredential credential = new OAuthCredentialService.StoredOAuthCredential(
                OAuthCredentialService.GOOGLE_PROVIDER,
                "user-gmail:11",
                "refresh-token-123",
                "access-token-123",
                java.time.Instant.parse("2026-03-26T12:00:00Z"),
                "scope",
                "Bearer",
                java.time.Instant.parse("2026-03-26T12:00:00Z"));

        UserGmailConfigService service = service(false, repository, Optional.of(credential));

        UserGmailConfigView view = service.viewForUser(11L).orElseThrow();

        assertTrue(view.refreshTokenConfigured());
        assertTrue(view.clientIdConfigured());
        assertTrue(view.clientSecretConfigured());
    }

    private UserGmailConfigService service(
            boolean encryptionConfigured,
            InMemoryUserGmailConfigRepository repository,
            Optional<OAuthCredentialService.StoredOAuthCredential> googleCredential) {
        UserGmailConfigService service = new UserGmailConfigService();
        service.repository = repository;
        service.secretEncryptionService = encryptionConfigured ? configuredSecrets() : unconfiguredSecrets();
        service.bridgeConfig = new TestConfig();
        service.oAuthCredentialService = new FakeOAuthCredentialService(googleCredential);
        return service;
    }

    private SecretEncryptionService unconfiguredSecrets() {
        SecretEncryptionService service = new SecretEncryptionService();
        service.tokenEncryptionKey = "replace-me";
        service.tokenEncryptionKeyId = "v1";
        return service;
    }

    private SecretEncryptionService configuredSecrets() {
        SecretEncryptionService service = new SecretEncryptionService();
        service.tokenEncryptionKey = java.util.Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        service.tokenEncryptionKeyId = "v1";
        return service;
    }

    private InMemoryUserGmailConfigRepository repositoryWithNoRow() {
        return new InMemoryUserGmailConfigRepository();
    }

    private static final class InMemoryUserGmailConfigRepository extends UserGmailConfigRepository {
        private UserGmailConfig row;

        @Override
        public Optional<UserGmailConfig> findByUserId(Long userId) {
            return row != null && row.userId.equals(userId) ? Optional.of(row) : Optional.empty();
        }

        @Override
        public void persist(UserGmailConfig entity) {
            this.row = entity;
        }
    }

    private static final class TestConfig implements BridgeConfig {
        @Override
        public boolean pollEnabled() {
            return true;
        }

        @Override
        public String pollInterval() {
            return "5m";
        }

        @Override
        public int fetchWindow() {
            return 50;
        }

        @Override
        public Gmail gmail() {
            return new Gmail() {
                @Override
                public String destinationUser() {
                    return "me";
                }

                @Override
                public String clientId() {
                    return "shared-client-id";
                }

                @Override
                public String clientSecret() {
                    return "shared-client-secret";
                }

                @Override
                public String refreshToken() {
                    return "";
                }

                @Override
                public String redirectUri() {
                    return "https://mail.example.test/api/google-oauth/callback";
                }

                @Override
                public boolean createMissingLabels() {
                    return true;
                }

                @Override
                public boolean neverMarkSpam() {
                    return false;
                }

                @Override
                public boolean processForCalendar() {
                    return false;
                }
            };
        }

        @Override
        public Microsoft microsoft() {
            return null;
        }

        @Override
        public java.util.List<Source> sources() {
            return java.util.List.of();
        }
    }

    private static final class FakeOAuthCredentialService extends OAuthCredentialService {
        private final Optional<StoredOAuthCredential> googleCredential;

        private FakeOAuthCredentialService(Optional<StoredOAuthCredential> googleCredential) {
            this.googleCredential = googleCredential;
        }

        @Override
        public Optional<StoredOAuthCredential> findGoogleCredential(String subjectKey) {
            return googleCredential.filter(credential -> credential.subjectKey().equals(subjectKey));
        }
    }
}
