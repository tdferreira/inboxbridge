package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        user.role = AppUser.Role.ADMIN;

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
        assertFalse(view.clientIdConfigured());
        assertFalse(view.clientSecretConfigured());
        assertTrue(view.sharedClientConfigured());
    }

    @Test
    void updateRejectsNonAdminOverrides() {
        UserGmailConfigService service = service(false, repositoryWithNoRow(), Optional.empty());
        AppUser user = new AppUser();
        user.id = 8L;
        user.role = AppUser.Role.USER;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.update(user, new UpdateUserGmailConfigRequest(
                        "me",
                        "",
                        "",
                        "",
                        "",
                        true,
                        false,
                        false)));

        assertEquals("Only admins can override advanced Gmail account settings from the admin UI.", error.getMessage());
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
        assertFalse(view.clientIdConfigured());
        assertFalse(view.clientSecretConfigured());
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
        assertFalse(view.clientIdConfigured());
        assertFalse(view.clientSecretConfigured());
        assertTrue(view.sharedClientConfigured());
    }

    @Test
    void resolveForUserFallsBackToSharedClientAndStoredOAuthCredentialWithoutConfigRow() {
        OAuthCredentialService.StoredOAuthCredential credential = new OAuthCredentialService.StoredOAuthCredential(
                OAuthCredentialService.GOOGLE_PROVIDER,
                "user-gmail:12",
                "refresh-token-xyz",
                "access-token-xyz",
                java.time.Instant.parse("2026-03-27T09:00:00Z"),
                "scope",
                "Bearer",
                java.time.Instant.parse("2026-03-27T09:00:00Z"));

        UserGmailConfigService service = service(true, repositoryWithNoRow(), Optional.of(credential));

        UserGmailConfigService.ResolvedUserGmailConfig resolved = service.resolveForUser(12L).orElseThrow();

        assertEquals("me", resolved.destinationUser());
        assertEquals("shared-client-id", resolved.clientId());
        assertEquals("shared-client-secret", resolved.clientSecret());
        assertEquals("refresh-token-xyz", resolved.refreshToken());
        assertEquals("https://mail.example.test/api/google-oauth/callback", resolved.redirectUri());
        assertTrue(resolved.createMissingLabels());
        assertFalse(resolved.neverMarkSpam());
        assertFalse(resolved.processForCalendar());
    }

    @Test
    void unlinkForUserClearsStoredRefreshTokensAndRevokesProviderAccess() {
        InMemoryUserGmailConfigRepository repository = repositoryWithNoRow();
        UserGmailConfig stored = new UserGmailConfig();
        stored.userId = 15L;
        stored.destinationUser = "me";
        stored.redirectUri = "https://mail.example.test/api/google-oauth/callback";
        stored.updatedAt = java.time.Instant.parse("2026-03-27T10:00:00Z");
        repository.persist(stored);

        OAuthCredentialService.StoredOAuthCredential credential = new OAuthCredentialService.StoredOAuthCredential(
                OAuthCredentialService.GOOGLE_PROVIDER,
                "user-gmail:15",
                "refresh-token-xyz",
                "access-token-xyz",
                java.time.Instant.parse("2026-03-27T09:00:00Z"),
                "scope",
                "Bearer",
                java.time.Instant.parse("2026-03-27T09:00:00Z"));

        FakeOAuthCredentialService credentialService = new FakeOAuthCredentialService(Optional.of(credential));
        FakeGoogleOAuthService googleOAuthService = new FakeGoogleOAuthService(true);
        UserGmailConfigService service = service(true, repository, Optional.of(credential));
        service.oAuthCredentialService = credentialService;
        service.googleOAuthService = googleOAuthService;

        UserGmailConfigService.GmailUnlinkResult result = service.unlinkForUser(15L);

        assertTrue(result.providerRevocationAttempted());
        assertTrue(result.providerRevoked());
        assertEquals("refresh-token-xyz", googleOAuthService.revokedToken);
        assertEquals("user-gmail:15", googleOAuthService.clearedSubjectKey);
        assertEquals("user-gmail:15", credentialService.deletedSubjectKey);
    }

    @Test
    void markGoogleAccessRevokedClearsStoredRefreshTokensWithoutProviderRevocation() {
        InMemoryUserGmailConfigRepository repository = repositoryWithNoRow();
        UserGmailConfig stored = new UserGmailConfig();
        stored.userId = 21L;
        stored.destinationUser = "me";
        stored.redirectUri = "https://mail.example.test/api/google-oauth/callback";
        stored.refreshTokenCiphertext = "stored-cipher";
        stored.refreshTokenNonce = "stored-nonce";
        repository.persist(stored);

        OAuthCredentialService.StoredOAuthCredential credential = new OAuthCredentialService.StoredOAuthCredential(
                OAuthCredentialService.GOOGLE_PROVIDER,
                "user-gmail:21",
                "refresh-token-xyz",
                "access-token-xyz",
                java.time.Instant.parse("2026-03-27T09:00:00Z"),
                "scope",
                "Bearer",
                java.time.Instant.parse("2026-03-27T09:00:00Z"));

        FakeOAuthCredentialService credentialService = new FakeOAuthCredentialService(Optional.of(credential));
        FakeGoogleOAuthService googleOAuthService = new FakeGoogleOAuthService(true);
        UserGmailConfigService service = service(true, repository, Optional.of(credential));
        service.oAuthCredentialService = credentialService;
        service.googleOAuthService = googleOAuthService;

        boolean changed = service.markGoogleAccessRevoked(new dev.inboxbridge.domain.GmailTarget(
                "user-gmail:21",
                21L,
                "john-doe",
                "me",
                "client",
                "secret",
                "",
                "https://mail.example.test/api/google-oauth/callback",
                true,
                false,
                false));

        assertTrue(changed);
        assertEquals("user-gmail:21", credentialService.deletedSubjectKey);
        assertEquals("user-gmail:21", googleOAuthService.clearedSubjectKey);
        assertFalse(service.viewForUser(21L).orElseThrow().refreshTokenConfigured());
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
        public boolean multiUserEnabled() {
            return true;
        }

        @Override
        public Security security() {
            return new Security() {
                @Override
                public Passkeys passkeys() {
                    return new Passkeys() {
                        @Override
                        public boolean enabled() {
                            return true;
                        }

                        @Override
                        public String rpId() {
                            return "localhost";
                        }

                        @Override
                        public String rpName() {
                            return "InboxBridge";
                        }

                        @Override
                        public String origins() {
                            return "https://localhost:3000";
                        }

                        @Override
                        public String challengeTtl() {
                            return "PT5M";
                        }
                    };
                }
            };
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
        private String deletedSubjectKey;

        private FakeOAuthCredentialService(Optional<StoredOAuthCredential> googleCredential) {
            this.googleCredential = googleCredential;
        }

        @Override
        public Optional<StoredOAuthCredential> findGoogleCredential(String subjectKey) {
            return googleCredential.filter(credential -> credential.subjectKey().equals(subjectKey));
        }

        @Override
        public boolean deleteGoogleCredential(String subjectKey) {
            this.deletedSubjectKey = subjectKey;
            return googleCredential.isPresent();
        }
    }

    private static final class FakeGoogleOAuthService extends GoogleOAuthService {
        private final boolean revokeResult;
        private String revokedToken;
        private String clearedSubjectKey;

        private FakeGoogleOAuthService(boolean revokeResult) {
            this.revokeResult = revokeResult;
        }

        @Override
        public boolean revokeToken(String token) {
            this.revokedToken = token;
            return revokeResult;
        }

        @Override
        public void clearCachedToken(String subjectKey) {
            this.clearedSubjectKey = subjectKey;
        }
    }
}
