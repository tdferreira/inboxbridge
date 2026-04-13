package dev.inboxbridge.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.SecretManagementStatusView;
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
    void reportsUnconfiguredStateWhenSecureStorageIsMissing() {
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
        assertEquals("UNCONFIGURED", view.mode());
        assertTrue(view.keyUsage().isEmpty());
    }

    private SecretManagementService configuredService() {
        SecretManagementService service = new SecretManagementService();
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(base64("fedcba9876543210fedcba9876543210"));
        provider.setTokenEncryptionKeyId("v2");
        provider.setTokenEncryptionLegacyKeys("v1:" + base64("0123456789abcdef0123456789abcdef"));
        service.setLocalSecretKeyProvider(provider);

        OAuthCredential oauthCredential = new OAuthCredential();
        oauthCredential.refreshTokenCiphertext = "cipher";
        oauthCredential.keyVersion = "LOCAL:v2";

        UserEmailAccount sourceMailbox = new UserEmailAccount();
        sourceMailbox.passwordCiphertext = "cipher";
        sourceMailbox.keyVersion = "LOCAL:v2";

        UserMailDestinationConfig destination = new UserMailDestinationConfig();
        destination.passwordCiphertext = "cipher";
        destination.keyVersion = "v1";

        UserGmailConfig gmailConfig = new UserGmailConfig();
        gmailConfig.clientSecretCiphertext = "cipher";
        gmailConfig.keyVersion = "LOCAL:v2";

        SystemOAuthAppSettings systemOAuth = new SystemOAuthAppSettings();
        systemOAuth.googleClientSecretCiphertext = "cipher";
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
    }
}
