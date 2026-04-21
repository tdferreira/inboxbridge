package dev.inboxbridge.service.security;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.SecretManagementPolicyConfig;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.persistence.SystemAuthSecuritySetting;
import dev.inboxbridge.persistence.SystemAuthSecuritySettingRepository;
import dev.inboxbridge.persistence.SystemOAuthAppSettings;
import dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository;
import dev.inboxbridge.persistence.SystemSecretRecoveryReview;
import dev.inboxbridge.persistence.SystemSecretRecoveryReviewRepository;
import dev.inboxbridge.persistence.SystemSecretReencryptionRequest;
import dev.inboxbridge.persistence.SystemSecretReencryptionRequestRepository;
import dev.inboxbridge.persistence.SystemSecretRetirementReview;
import dev.inboxbridge.persistence.SystemSecretRetirementReviewRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import dev.inboxbridge.service.mail.EnvSourceService;
import dev.inboxbridge.service.oauth.OAuthCredentialService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.remote.RemoteSessionService;

final class SecretManagementTestFixtureSupport {

    private SecretManagementTestFixtureSupport() {
    }

    static SecretManagementFixture createFixture(
            SecretProviderMode sourceMode,
            TransitSecretProvider transitSecretProvider,
            TransitProviderSettings openbao,
            TransitProviderSettings vault) {
        LocalSecretKeyProvider localProvider = localProvider();
        SecretProviderResolver resolver = resolver(localProvider, transitSecretProvider, sourceMode, openbao, vault);
        SecretEncryptionService encryptionService = encryptionService(localProvider, resolver, transitSecretProvider);

        OAuthCredential oauthCredential = new OAuthCredential();
        SecretEncryptionService.EncryptedValue oauthRefresh = encryptionService.encrypt("oauth-refresh", "GOOGLE:gmail-destination:refresh");
        oauthCredential.provider = "GOOGLE";
        oauthCredential.subjectKey = "gmail-destination";
        oauthCredential.refreshTokenCiphertext = oauthRefresh.ciphertextBase64();
        oauthCredential.refreshTokenNonce = oauthRefresh.nonceBase64();
        oauthCredential.keyVersion = encryptionService.keyVersion();

        UserEmailAccount sourceMailbox = new UserEmailAccount();
        SecretEncryptionService.EncryptedValue sourcePassword = encryptionService.encrypt("source-password", "user-bridge:1:source-a:password");
        sourceMailbox.userId = 1L;
        sourceMailbox.emailAccountId = "source-a";
        sourceMailbox.passwordCiphertext = sourcePassword.ciphertextBase64();
        sourceMailbox.passwordNonce = sourcePassword.nonceBase64();
        sourceMailbox.keyVersion = encryptionService.keyVersion();

        UserMailDestinationConfig destination = new UserMailDestinationConfig();
        SecretEncryptionService.EncryptedValue destinationPassword = encryptionService.encrypt("destination-password", "user-destination:1:password");
        destination.userId = 1L;
        destination.passwordCiphertext = destinationPassword.ciphertextBase64();
        destination.passwordNonce = destinationPassword.nonceBase64();
        destination.keyVersion = encryptionService.keyVersion();

        UserGmailConfig gmailConfig = new UserGmailConfig();
        SecretEncryptionService.EncryptedValue gmailSecret = encryptionService.encrypt("gmail-secret", "user-gmail:1:client-secret");
        gmailConfig.userId = 1L;
        gmailConfig.clientSecretCiphertext = gmailSecret.ciphertextBase64();
        gmailConfig.clientSecretNonce = gmailSecret.nonceBase64();
        gmailConfig.keyVersion = encryptionService.keyVersion();

        SystemOAuthAppSettings systemOAuth = new SystemOAuthAppSettings();
        SecretEncryptionService.EncryptedValue systemGoogleSecret = encryptionService.encrypt("system-google-secret", "system-oauth:google-client-secret");
        systemOAuth.googleClientSecretCiphertext = systemGoogleSecret.ciphertextBase64();
        systemOAuth.googleClientSecretNonce = systemGoogleSecret.nonceBase64();
        systemOAuth.keyVersion = encryptionService.keyVersion();

        SystemAuthSecuritySetting authSecurity = new SystemAuthSecuritySetting();
        authSecurity.registrationTurnstileSecretCiphertext = "";
        authSecurity.keyVersion = encryptionService.keyVersion();

        SecretManagementService service = new SecretManagementService();
        service.setLocalSecretKeyProvider(localProvider);
        service.setSecretProviderResolver(resolver);
        service.setSecretEncryptionService(encryptionService);
        service.setOAuthCredentialRepository(new InMemoryOAuthCredentialRepository(List.of(oauthCredential)));
        service.setUserEmailAccountRepository(new InMemoryUserEmailAccountRepository(List.of(sourceMailbox)));
        service.setUserMailDestinationConfigRepository(new InMemoryUserMailDestinationConfigRepository(List.of(destination)));
        service.setUserGmailConfigRepository(new InMemoryUserGmailConfigRepository(List.of(gmailConfig)));
        service.setSystemOAuthAppSettingsRepository(new InMemorySystemOAuthAppSettingsRepository(systemOAuth));
        service.setSystemAuthSecuritySettingRepository(new InMemorySystemAuthSecuritySettingRepository(authSecurity));
        service.setSystemSecretReencryptionRequestRepository(new InMemorySystemSecretReencryptionRequestRepository());
        service.setSystemSecretRecoveryReviewRepository(new InMemorySystemSecretRecoveryReviewRepository());
        service.setSystemSecretRetirementReviewRepository(new InMemorySystemSecretRetirementReviewRepository());
        service.setSecretManagementPolicyConfig(new FixedSecretManagementPolicyConfig());
        service.setExtensionSessionService(new StubExtensionSessionService());
        service.setRemoteSessionService(new StubRemoteSessionService());
        service.setOAuthCredentialService(new StubOAuthCredentialService());
        service.setEnvSourceService(new EnvSourceService() {
            @Override
            public long configuredSourceCountIgnoringPolicy() {
                return 1;
            }
        });
        service.setSystemOAuthAppSettingsService(new SystemOAuthAppSettingsService() {
            @Override
            public boolean envManagedGoogleRefreshTokenConfigured() {
                return true;
            }
        });
        return new SecretManagementFixture(service, localProvider, resolver, encryptionService);
    }

    static void applyActiveMode(
            SecretProviderResolver resolver,
            SecretProviderMode activeMode,
            TransitProviderSettings openbao,
            TransitProviderSettings vault) {
        resolver.setProviderMode(activeMode.name());
        applyTransitSettings(resolver, openbao, vault);
    }

    static AppUser adminUser() {
        AppUser user = new AppUser();
        user.id = 1L;
        user.username = "admin";
        user.role = AppUser.Role.ADMIN;
        return user;
    }

    private static LocalSecretKeyProvider localProvider() {
        LocalSecretKeyProvider provider = new LocalSecretKeyProvider();
        provider.setTokenEncryptionKey(base64("fedcba9876543210fedcba9876543210"));
        provider.setTokenEncryptionKeyId("v2");
        provider.setTokenEncryptionLegacyKeys("v1:" + base64("0123456789abcdef0123456789abcdef"));
        return provider;
    }

    private static SecretProviderResolver resolver(
            LocalSecretKeyProvider localProvider,
            TransitSecretProvider transitSecretProvider,
            SecretProviderMode activeMode,
            TransitProviderSettings openbao,
            TransitProviderSettings vault) {
        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(localProvider);
        resolver.setTransitSecretProvider(transitSecretProvider);
        resolver.setProviderMode(activeMode.name());
        applyTransitSettings(resolver, openbao, vault);
        return resolver;
    }

    private static SecretEncryptionService encryptionService(
            LocalSecretKeyProvider localProvider,
            SecretProviderResolver resolver,
            TransitSecretProvider transitSecretProvider) {
        SecretEncryptionService encryptionService = new SecretEncryptionService();
        encryptionService.setLocalSecretKeyProvider(localProvider);
        encryptionService.setSecretProviderResolver(resolver);
        encryptionService.setTransitSecretProvider(transitSecretProvider);
        return encryptionService;
    }

    private static void applyTransitSettings(
            SecretProviderResolver resolver,
            TransitProviderSettings openbao,
            TransitProviderSettings vault) {
        if (openbao != null) {
            resolver.setOpenbaoUrl(openbao.baseUrl());
            resolver.setOpenbaoToken(openbao.token());
            resolver.setOpenbaoMount(openbao.mount());
            resolver.setOpenbaoKey(openbao.keyName());
        }
        if (vault != null) {
            resolver.setVaultUrl(vault.baseUrl());
            resolver.setVaultToken(vault.token());
            resolver.setVaultMount(vault.mount());
            resolver.setVaultKey(vault.keyName());
        }
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    record TransitProviderSettings(
            SecretProviderMode mode,
            String baseUrl,
            String token,
            String mount,
            String keyName) {
    }

    record SecretManagementFixture(
            SecretManagementService service,
            LocalSecretKeyProvider localSecretKeyProvider,
            SecretProviderResolver resolver,
            SecretEncryptionService encryptionService) {
    }

    private static final class FixedSecretManagementPolicyConfig implements SecretManagementPolicyConfig {
        @Override
        public boolean allowEnvManagedMailboxSecrets() {
            return true;
        }

        @Override
        public Duration reencryptionCooldown() {
            return Duration.ZERO;
        }

        @Override
        public boolean allowImmediateReencryptOverride() {
            return false;
        }

        @Override
        public Duration reauthenticationTtl() {
            return Duration.ZERO;
        }
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

    private static final class InMemorySystemSecretReencryptionRequestRepository extends SystemSecretReencryptionRequestRepository {
        private SystemSecretReencryptionRequest value;

        @Override
        public Optional<SystemSecretReencryptionRequest> findSingleton() {
            return Optional.ofNullable(value);
        }

        @Override
        public void persist(SystemSecretReencryptionRequest entity) {
            value = entity;
        }
    }

    private static final class InMemorySystemSecretRetirementReviewRepository extends SystemSecretRetirementReviewRepository {
        private final List<SystemSecretRetirementReview> values = new java.util.ArrayList<>();
        private long nextId = 1L;

        @Override
        public Optional<SystemSecretRetirementReview> findLatest() {
            return values.stream()
                    .sorted(java.util.Comparator
                            .comparing((SystemSecretRetirementReview review) -> review.reviewedAt)
                            .thenComparing(review -> review.id)
                            .reversed())
                    .findFirst();
        }

        @Override
        public List<SystemSecretRetirementReview> listRecent(int maxResults) {
            return values.stream()
                    .sorted(java.util.Comparator
                            .comparing((SystemSecretRetirementReview review) -> review.reviewedAt)
                            .thenComparing(review -> review.id)
                            .reversed())
                    .limit(maxResults)
                    .toList();
        }

        @Override
        public void persist(SystemSecretRetirementReview entity) {
            if (entity.id == null) {
                entity.id = nextId++;
            }
            values.removeIf(existing -> existing.id.equals(entity.id));
            values.add(entity);
        }
    }

    private static final class InMemorySystemSecretRecoveryReviewRepository extends SystemSecretRecoveryReviewRepository {
        private final List<SystemSecretRecoveryReview> values = new java.util.ArrayList<>();
        private long nextId = 1L;

        @Override
        public Optional<SystemSecretRecoveryReview> findLatest() {
            return values.stream()
                    .sorted(java.util.Comparator
                            .comparing((SystemSecretRecoveryReview review) -> review.reviewedAt)
                            .thenComparing(review -> review.id)
                            .reversed())
                    .findFirst();
        }

        @Override
        public List<SystemSecretRecoveryReview> listRecent(int maxResults) {
            return values.stream()
                    .sorted(java.util.Comparator
                            .comparing((SystemSecretRecoveryReview review) -> review.reviewedAt)
                            .thenComparing(review -> review.id)
                            .reversed())
                    .limit(maxResults)
                    .toList();
        }

        @Override
        public void persist(SystemSecretRecoveryReview entity) {
            if (entity.id == null) {
                entity.id = nextId++;
            }
            values.removeIf(existing -> existing.id.equals(entity.id));
            values.add(entity);
        }
    }

    private static final class StubExtensionSessionService extends ExtensionSessionService {
        @Override
        public int revokeAllSessionsForAllUsers() {
            return 0;
        }
    }

    private static final class StubRemoteSessionService extends RemoteSessionService {
        @Override
        public int invalidateAllSessions() {
            return 0;
        }
    }

    private static final class StubOAuthCredentialService extends OAuthCredentialService {
        @Override
        public int clearAllAccessTokens() {
            return 0;
        }
    }
}
