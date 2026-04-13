package dev.inboxbridge.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.SecretManagementStatusView;
import dev.inboxbridge.dto.SecretReencryptionResultView;
import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.persistence.SystemAuthSecuritySetting;
import dev.inboxbridge.persistence.SystemAuthSecuritySettingRepository;
import dev.inboxbridge.persistence.SystemOAuthAppSettings;
import dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;

class SecretManagementServiceTest {

    @Test
    void reportsKeyUsageAcrossSecretBearingAreas() {
        SecretManagementService service = configuredService();

        SecretManagementStatusView view = service.status();

        assertTrue(view.secureStorageConfigured());
        assertEquals("LOCAL", view.mode());
        assertEquals("LOCAL", view.providerId());
        assertTrue(view.providerHealthy());
        assertTrue(view.providerWritable());
        assertEquals("Local secret provider is ready.", view.providerStatusMessage());
        assertEquals("LOCAL:v2", view.activeKeyVersion());
        assertEquals("v2", view.activeKeyId());
        assertEquals(List.of("v1"), view.configuredLegacyKeyIds());
        assertEquals(5, view.protectedRecordCount());
        assertEquals(3, view.activeKeyRecordCount());
        assertEquals(2, view.nonActiveKeyRecordCount());
        assertEquals(0, view.unavailableKeyRecordCount());
        assertFalse(view.safeToRetireLegacyKeys());
        assertEquals(2, view.keyUsage().size());
        assertEquals("LOCAL:v2", view.keyUsage().getFirst().keyVersion());
        assertEquals("v1", view.keyUsage().get(1).keyVersion());
        assertTrue(view.keyUsage().get(1).availableForDecryption());
    }

    @Test
    void flagsUnavailableKeyReferencesWhenLegacyKeyWasRemoved() {
        SecretManagementService service = configuredService();
        service.localSecretKeyProvider.setTokenEncryptionLegacyKeys("");

        SecretManagementStatusView view = service.status();

        assertEquals(2, view.unavailableKeyRecordCount());
        assertFalse(view.keyUsage().get(1).availableForDecryption());
        assertFalse(view.safeToRetireLegacyKeys());
    }

    @Test
    void reportsLocalModeAsUnavailableWhenSecureStorageIsMissing() {
        SecretManagementService service = new SecretManagementService();
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey("replace-me");
        provider.setTokenEncryptionKeyId("v1");
        service.setLocalSecretKeyProvider(provider);
        service.setOAuthCredentialRepository(new InMemoryOAuthCredentialRepository(List.of()));
        service.setUserEmailAccountRepository(new InMemoryUserEmailAccountRepository(List.of()));
        service.setUserMailDestinationConfigRepository(new InMemoryUserMailDestinationConfigRepository(List.of()));
        service.setUserGmailConfigRepository(new InMemoryUserGmailConfigRepository(List.of()));
        service.setSystemOAuthAppSettingsRepository(new InMemorySystemOAuthAppSettingsRepository(null));
        service.setSystemAuthSecuritySettingRepository(new InMemorySystemAuthSecuritySettingRepository(null));

        SecretManagementStatusView view = service.status();

        assertFalse(view.secureStorageConfigured());
        assertEquals("LOCAL", view.mode());
        assertFalse(view.providerHealthy());
        assertFalse(view.providerWritable());
        assertEquals("Secure token storage is not configured. Set SECURITY_TOKEN_ENCRYPTION_KEY.", view.providerStatusMessage());
        assertTrue(view.keyUsage().isEmpty());
    }

    @Test
    void reportsUnsupportedTransitModeInStatus() {
        SecretManagementService service = configuredService();
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(service.localSecretKeyProvider);
        resolver.setProviderMode("VAULT_TRANSIT");
        resolver.setVaultUrl("https://vault.internal");
        resolver.setVaultToken("token");
        resolver.setVaultMount("transit");
        resolver.setVaultKey("inboxbridge");
        service.setSecretProviderResolver(resolver);

        SecretManagementStatusView view = service.status();

        assertFalse(view.secureStorageConfigured());
        assertEquals("VAULT_TRANSIT", view.mode());
        assertEquals("VAULT_TRANSIT", view.providerId());
        assertFalse(view.providerHealthy());
        assertFalse(view.providerWritable());
        assertEquals(
                "Secret provider VAULT_TRANSIT is configured, but transit-backed secret encryption is not implemented yet.",
                view.providerStatusMessage());
        assertTrue(view.keyUsage().isEmpty());
    }

    @Test
    void reencryptAllStoredSecretsRewritesLegacyRecordsUnderActiveKey() {
        SecretManagementService service = configuredService();

        SecretReencryptionResultView result = service.reencryptAllStoredSecrets();
        SecretManagementStatusView status = service.status();

        assertEquals("LOCAL:v2", result.activeKeyVersion());
        assertEquals(2, result.totalRecordsUpdated());
        assertEquals(2, result.totalSecretValuesReencrypted());
        assertEquals("destination-mailboxes", result.areas().get(2).area());
        assertEquals(1, result.areas().get(2).recordsUpdated());
        assertEquals("system-oauth", result.areas().get(4).area());
        assertEquals(1, result.areas().get(4).recordsUpdated());
        assertEquals(5, status.activeKeyRecordCount());
        assertEquals(0, status.nonActiveKeyRecordCount());
        assertTrue(status.safeToRetireLegacyKeys());
    }

    private SecretManagementService configuredService() {
        SecretManagementService service = new SecretManagementService();
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(base64("fedcba9876543210fedcba9876543210"));
        provider.setTokenEncryptionKeyId("v2");
        provider.setTokenEncryptionLegacyKeys("v1:" + base64("0123456789abcdef0123456789abcdef"));
        service.setLocalSecretKeyProvider(provider);
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(provider);
        resolver.setProviderMode("LOCAL");
        service.setSecretProviderResolver(resolver);
        SecretEncryptionService secretEncryptionService = new SecretEncryptionService();
        secretEncryptionService.setLocalSecretKeyProvider(provider);
        secretEncryptionService.setSecretProviderResolver(resolver);
        service.setSecretEncryptionService(secretEncryptionService);
        SecretEncryptionService legacySecretEncryptionService = new SecretEncryptionService();
        legacySecretEncryptionService.setTokenEncryptionKey(base64("0123456789abcdef0123456789abcdef"));
        legacySecretEncryptionService.setTokenEncryptionKeyId("v1");

        OAuthCredential oauthCredential = new OAuthCredential();
        SecretEncryptionService.EncryptedValue oauthRefresh = secretEncryptionService.encrypt("oauth-refresh", "GOOGLE:gmail-destination:refresh");
        oauthCredential.refreshTokenCiphertext = oauthRefresh.ciphertextBase64();
        oauthCredential.refreshTokenNonce = oauthRefresh.nonceBase64();
        oauthCredential.keyVersion = secretEncryptionService.keyVersion();

        UserEmailAccount sourceMailbox = new UserEmailAccount();
        SecretEncryptionService.EncryptedValue sourcePassword = secretEncryptionService.encrypt("source-password", "user-bridge:1:source-a:password");
        sourceMailbox.userId = 1L;
        sourceMailbox.emailAccountId = "source-a";
        sourceMailbox.passwordCiphertext = sourcePassword.ciphertextBase64();
        sourceMailbox.passwordNonce = sourcePassword.nonceBase64();
        sourceMailbox.keyVersion = secretEncryptionService.keyVersion();

        UserMailDestinationConfig destination = new UserMailDestinationConfig();
        SecretEncryptionService.EncryptedValue destinationPassword = legacySecretEncryptionService.encrypt("destination-password", "user-destination:1:password");
        destination.userId = 1L;
        destination.passwordCiphertext = destinationPassword.ciphertextBase64();
        destination.passwordNonce = destinationPassword.nonceBase64();
        destination.keyVersion = "v1";

        UserGmailConfig gmailConfig = new UserGmailConfig();
        SecretEncryptionService.EncryptedValue gmailSecret = secretEncryptionService.encrypt("gmail-secret", "user-gmail:1:client-secret");
        gmailConfig.userId = 1L;
        gmailConfig.clientSecretCiphertext = gmailSecret.ciphertextBase64();
        gmailConfig.clientSecretNonce = gmailSecret.nonceBase64();
        gmailConfig.keyVersion = secretEncryptionService.keyVersion();

        SystemOAuthAppSettings systemOAuth = new SystemOAuthAppSettings();
        SecretEncryptionService.EncryptedValue systemGoogleSecret = legacySecretEncryptionService.encrypt("system-google-secret", "system-oauth:google-client-secret");
        systemOAuth.googleClientSecretCiphertext = systemGoogleSecret.ciphertextBase64();
        systemOAuth.googleClientSecretNonce = systemGoogleSecret.nonceBase64();
        systemOAuth.keyVersion = "v1";

        SystemAuthSecuritySetting authSecurity = new SystemAuthSecuritySetting();
        authSecurity.registrationTurnstileSecretCiphertext = "";
        authSecurity.keyVersion = "LOCAL:v2";

        service.setOAuthCredentialRepository(new InMemoryOAuthCredentialRepository(List.of(oauthCredential)));
        service.setUserEmailAccountRepository(new InMemoryUserEmailAccountRepository(List.of(sourceMailbox)));
        service.setUserMailDestinationConfigRepository(new InMemoryUserMailDestinationConfigRepository(List.of(destination)));
        service.setUserGmailConfigRepository(new InMemoryUserGmailConfigRepository(List.of(gmailConfig)));
        service.setSystemOAuthAppSettingsRepository(new InMemorySystemOAuthAppSettingsRepository(systemOAuth));
        service.setSystemAuthSecuritySettingRepository(new InMemorySystemAuthSecuritySettingRepository(authSecurity));
        return service;
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    private static final class InMemoryOAuthCredentialRepository extends OAuthCredentialRepository {
        private final List<OAuthCredential> values;

        private InMemoryOAuthCredentialRepository(List<OAuthCredential> values) {
            this.values = values;
        }

        @Override
        public List<OAuthCredential> listAll() {
            return values;
        }

        @Override
        public void persist(OAuthCredential entity) {
        }
    }

    private static final class InMemoryUserEmailAccountRepository extends UserEmailAccountRepository {
        private final List<UserEmailAccount> values;

        private InMemoryUserEmailAccountRepository(List<UserEmailAccount> values) {
            this.values = values;
        }

        @Override
        public List<UserEmailAccount> listAll() {
            return values;
        }

        @Override
        public void persist(UserEmailAccount entity) {
        }
    }

    private static final class InMemoryUserMailDestinationConfigRepository extends UserMailDestinationConfigRepository {
        private final List<UserMailDestinationConfig> values;

        private InMemoryUserMailDestinationConfigRepository(List<UserMailDestinationConfig> values) {
            this.values = values;
        }

        @Override
        public List<UserMailDestinationConfig> listAll() {
            return values;
        }

        @Override
        public void persist(UserMailDestinationConfig entity) {
        }
    }

    private static final class InMemoryUserGmailConfigRepository extends UserGmailConfigRepository {
        private final List<UserGmailConfig> values;

        private InMemoryUserGmailConfigRepository(List<UserGmailConfig> values) {
            this.values = values;
        }

        @Override
        public List<UserGmailConfig> listAll() {
            return values;
        }

        @Override
        public void persist(UserGmailConfig entity) {
        }
    }

    private static final class InMemorySystemOAuthAppSettingsRepository extends SystemOAuthAppSettingsRepository {
        private final SystemOAuthAppSettings value;

        private InMemorySystemOAuthAppSettingsRepository(SystemOAuthAppSettings value) {
            this.value = value;
        }

        @Override
        public Optional<SystemOAuthAppSettings> findSingleton() {
            return Optional.ofNullable(value);
        }

        @Override
        public void persist(SystemOAuthAppSettings entity) {
        }
    }

    private static final class InMemorySystemAuthSecuritySettingRepository extends SystemAuthSecuritySettingRepository {
        private final SystemAuthSecuritySetting value;

        private InMemorySystemAuthSecuritySettingRepository(SystemAuthSecuritySetting value) {
            this.value = value;
        }

        @Override
        public Optional<SystemAuthSecuritySetting> findSingleton() {
            return Optional.ofNullable(value);
        }

        @Override
        public void persist(SystemAuthSecuritySetting entity) {
        }
    }
}
