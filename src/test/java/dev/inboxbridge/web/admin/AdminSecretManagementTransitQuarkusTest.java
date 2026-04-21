package dev.inboxbridge.web.admin;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollAction;
import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.persistence.SystemAuthSecuritySetting;
import dev.inboxbridge.persistence.SystemAuthSecuritySettingRepository;
import dev.inboxbridge.persistence.SystemOAuthAppSettings;
import dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository;
import dev.inboxbridge.persistence.SystemSecretReencryptionRequestRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import dev.inboxbridge.service.security.LocalSecretKeyProvider;
import dev.inboxbridge.service.security.SecretEncryptionService;
import dev.inboxbridge.service.security.SecretProviderResolver;
import dev.inboxbridge.testsupport.PostgresQuarkusTestProfile;
import dev.inboxbridge.testsupport.PostgresTestResource;
import dev.inboxbridge.testsupport.SecretTransitProvidersTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;

/**
 * Verifies the operator-facing secret-management HTTP flow against real
 * PostgreSQL-backed Quarkus boot and live transit providers, not only the
 * lower-level service fixtures.
 */
@QuarkusTest
@TestProfile(PostgresQuarkusTestProfile.class)
@QuarkusTestResource(PostgresTestResource.class)
@QuarkusTestResource(SecretTransitProvidersTestResource.class)
class AdminSecretManagementTransitQuarkusTest {

    private static final String SESSION_COOKIE = "inboxbridge_session";
    private static final String CSRF_COOKIE = "inboxbridge_csrf";
    private static final String CSRF_HEADER = "X-InboxBridge-CSRF";

    @Inject
    OAuthCredentialRepository oauthCredentialRepository;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    UserMailDestinationConfigRepository userMailDestinationConfigRepository;

    @Inject
    UserGmailConfigRepository userGmailConfigRepository;

    @Inject
    SystemOAuthAppSettingsRepository systemOAuthAppSettingsRepository;

    @Inject
    SystemAuthSecuritySettingRepository systemAuthSecuritySettingRepository;

    @Inject
    SystemSecretReencryptionRequestRepository systemSecretReencryptionRequestRepository;

    @BeforeEach
    void seedLegacyLocalSecrets() {
        QuarkusTransaction.requiringNew().run(() -> {
            oauthCredentialRepository.deleteAll();
            userEmailAccountRepository.deleteAll();
            userMailDestinationConfigRepository.deleteAll();
            userGmailConfigRepository.deleteAll();
            systemOAuthAppSettingsRepository.deleteAll();
            systemAuthSecuritySettingRepository.deleteAll();
            systemSecretReencryptionRequestRepository.deleteAll();

            SecretEncryptionService encryptionService = localEncryptionService();
            Instant now = Instant.now();

            OAuthCredential oauthCredential = new OAuthCredential();
            oauthCredential.provider = "GOOGLE";
            oauthCredential.subjectKey = "gmail-destination";
            var oauthRefresh = encryptionService.encrypt("oauth-refresh", "GOOGLE:gmail-destination:refresh");
            oauthCredential.refreshTokenCiphertext = oauthRefresh.ciphertextBase64();
            oauthCredential.refreshTokenNonce = oauthRefresh.nonceBase64();
            oauthCredential.keyVersion = encryptionService.keyVersion();
            oauthCredential.createdAt = now;
            oauthCredential.updatedAt = now;
            oauthCredential.lastRefreshedAt = now;
            oauthCredentialRepository.persist(oauthCredential);

            UserEmailAccount sourceMailbox = new UserEmailAccount();
            sourceMailbox.userId = 1L;
            sourceMailbox.emailAccountId = "source-a";
            sourceMailbox.enabled = true;
            sourceMailbox.enableAfterOauthConnect = false;
            sourceMailbox.protocol = InboxBridgeConfig.Protocol.IMAP;
            sourceMailbox.host = "imap.example.com";
            sourceMailbox.port = 993;
            sourceMailbox.tls = true;
            sourceMailbox.authMethod = InboxBridgeConfig.AuthMethod.PASSWORD;
            sourceMailbox.oauthProvider = InboxBridgeConfig.OAuthProvider.NONE;
            sourceMailbox.username = "source@example.com";
            var sourcePassword = encryptionService.encrypt("source-password", "user-bridge:1:source-a:password");
            sourceMailbox.passwordCiphertext = sourcePassword.ciphertextBase64();
            sourceMailbox.passwordNonce = sourcePassword.nonceBase64();
            sourceMailbox.keyVersion = encryptionService.keyVersion();
            sourceMailbox.folderName = "INBOX";
            sourceMailbox.unreadOnly = false;
            sourceMailbox.fetchMode = SourceFetchMode.POLLING;
            sourceMailbox.customLabel = "Imported/SourceA";
            sourceMailbox.markReadAfterPoll = false;
            sourceMailbox.postPollAction = SourcePostPollAction.NONE;
            sourceMailbox.createdAt = now;
            sourceMailbox.updatedAt = now;
            userEmailAccountRepository.persist(sourceMailbox);

            UserMailDestinationConfig destination = new UserMailDestinationConfig();
            destination.userId = 1L;
            destination.provider = "GENERIC_IMAP";
            destination.host = "imap.destination.example.com";
            destination.port = 993;
            destination.tls = true;
            destination.authMethod = "PASSWORD";
            destination.oauthProvider = "NONE";
            destination.username = "destination@example.com";
            var destinationPassword = encryptionService.encrypt("destination-password", "user-destination:1:password");
            destination.passwordCiphertext = destinationPassword.ciphertextBase64();
            destination.passwordNonce = destinationPassword.nonceBase64();
            destination.keyVersion = encryptionService.keyVersion();
            destination.folderName = "INBOX";
            destination.updatedAt = now;
            userMailDestinationConfigRepository.persist(destination);

            UserGmailConfig gmailConfig = new UserGmailConfig();
            gmailConfig.userId = 1L;
            gmailConfig.destinationUser = "me";
            gmailConfig.linkedMailboxAddress = "admin@example.com";
            var gmailSecret = encryptionService.encrypt("gmail-secret", "user-gmail:1:client-secret");
            gmailConfig.clientSecretCiphertext = gmailSecret.ciphertextBase64();
            gmailConfig.clientSecretNonce = gmailSecret.nonceBase64();
            gmailConfig.keyVersion = encryptionService.keyVersion();
            gmailConfig.redirectUri = "https://localhost:3000/oauth/google/callback";
            gmailConfig.createMissingLabels = true;
            gmailConfig.neverMarkSpam = false;
            gmailConfig.processForCalendar = false;
            gmailConfig.updatedAt = now;
            userGmailConfigRepository.persist(gmailConfig);

            SystemOAuthAppSettings systemOAuth = new SystemOAuthAppSettings();
            systemOAuth.id = 1L;
            var systemGoogleSecret = encryptionService.encrypt("system-google-secret", "system-oauth:google-client-secret");
            systemOAuth.googleClientSecretCiphertext = systemGoogleSecret.ciphertextBase64();
            systemOAuth.googleClientSecretNonce = systemGoogleSecret.nonceBase64();
            systemOAuth.keyVersion = encryptionService.keyVersion();
            systemOAuth.updatedAt = now;
            systemOAuthAppSettingsRepository.persist(systemOAuth);

            SystemAuthSecuritySetting authSecurity = new SystemAuthSecuritySetting();
            authSecurity.id = SystemAuthSecuritySetting.SINGLETON_ID;
            authSecurity.updatedAt = now;
            authSecurity.keyVersion = encryptionService.keyVersion();
            systemAuthSecuritySettingRepository.persist(authSecurity);
        });
    }

    @Test
    void adminSecretManagementEndpointsCanGuideAndExecuteTransitMigration() {
        BrowserSession session = loginAsAdmin();

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/admin/secret-management")
                .then()
                .statusCode(200)
                .body("mode", equalTo("VAULT_TRANSIT"))
                .body("providerId", equalTo("VAULT_TRANSIT"))
                .body("providerWritable", equalTo(true))
                .body("protectedRecordCount", equalTo(5))
                .body("activeKeyVersion", equalTo("VAULT_TRANSIT:" + SecretTransitProvidersTestResource.VAULT_KEY))
                .body("nonActiveKeyRecordCount", equalTo(5));

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .queryParam("targetMode", "OPENBAO_TRANSIT")
                .when().get("/api/admin/secret-management/migration-guide")
                .then()
                .statusCode(200)
                .body("currentMode", equalTo("VAULT_TRANSIT"))
                .body("targetMode", equalTo("OPENBAO_TRANSIT"))
                .body("targetReady", equalTo(true))
                .body("checks.size()", notNullValue());

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/admin/secret-management/report")
                .then()
                .statusCode(200)
                .body("status.mode", equalTo("VAULT_TRANSIT"))
                .body("status.protectedRecordCount", equalTo(5))
                .body("status.activeKeyVersion", equalTo("VAULT_TRANSIT:" + SecretTransitProvidersTestResource.VAULT_KEY))
                .body("saveChecklist.size()", notNullValue());

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .cookie(CSRF_COOKIE, session.csrfToken())
                .header(CSRF_HEADER, session.csrfToken())
                .contentType("application/json")
                .body(Map.of("password", "nimda"))
                .when().post("/api/admin/secret-management/re-auth/password")
                .then()
                .statusCode(200)
                .body("reauthenticationSatisfied", equalTo(true))
                .body("reauthenticationExpiresAt", notNullValue());

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .cookie(CSRF_COOKIE, session.csrfToken())
                .header(CSRF_HEADER, session.csrfToken())
                .contentType("application/json")
                .body(Map.of(
                        "immediateExecutionOverride", true,
                        "revokeBrowserExtensionSessions", false,
                        "revokeRemoteSessions", false,
                        "clearCachedOAuthAccessTokens", false))
                .when().post("/api/admin/secret-management/re-encrypt")
                .then()
                .statusCode(200)
                .body("operationStatus", equalTo("COMPLETED"))
                .body("activeKeyVersion", equalTo("VAULT_TRANSIT:" + SecretTransitProvidersTestResource.VAULT_KEY))
                .body("totalRecordsUpdated", equalTo(5))
                .body("totalSecretValuesReencrypted", equalTo(5))
                .body("totalFullReencryptionCount", equalTo(5))
                .body("verification.passed", equalTo(true))
                .body("verification.operatorSaveItems.size()", notNullValue());

        given()
                .cookie(SESSION_COOKIE, session.sessionToken())
                .when().get("/api/admin/secret-management")
                .then()
                .statusCode(200)
                .body("mode", equalTo("VAULT_TRANSIT"))
                .body("protectedRecordCount", equalTo(5))
                .body("activeKeyRecordCount", equalTo(5))
                .body("nonActiveKeyRecordCount", equalTo(0))
                .body("safeToRetireLegacyKeys", equalTo(true))
                .body("legacyKeyRetirementReady", equalTo(true));

        assertEquals(6L, oauthCredentialRepository.count() + userEmailAccountRepository.count()
                + userMailDestinationConfigRepository.count() + userGmailConfigRepository.count()
                + systemOAuthAppSettingsRepository.count() + systemAuthSecuritySettingRepository.count());
        assertTrue(systemSecretReencryptionRequestRepository.findSingleton().isPresent());
    }

    private BrowserSession loginAsAdmin() {
        ValidatableResponse response = given()
                .contentType("application/json")
                .body(Map.of(
                        "username", "admin",
                        "password", "nimda"))
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("status", equalTo("AUTHENTICATED"));

        return new BrowserSession(
                response.extract().cookie(SESSION_COOKIE),
                response.extract().cookie(CSRF_COOKIE));
    }

    private SecretEncryptionService localEncryptionService() {
        LocalSecretKeyProvider localProvider = new LocalSecretKeyProvider();
        localProvider.setTokenEncryptionKey(SecretTransitProvidersTestResource.LOCAL_ACTIVE_KEY_BASE64);
        localProvider.setTokenEncryptionKeyId(SecretTransitProvidersTestResource.LOCAL_ACTIVE_KEY_ID);
        localProvider.setTokenEncryptionLegacyKeys(
                SecretTransitProvidersTestResource.LOCAL_LEGACY_KEY_ID + ":" + SecretTransitProvidersTestResource.LOCAL_LEGACY_KEY_BASE64);

        SecretProviderResolver resolver = new SecretProviderResolver();
        resolver.setLocalSecretKeyProvider(localProvider);
        resolver.setProviderMode("LOCAL");

        SecretEncryptionService encryptionService = new SecretEncryptionService();
        encryptionService.setLocalSecretKeyProvider(localProvider);
        encryptionService.setSecretProviderResolver(resolver);
        return encryptionService;
    }

    private record BrowserSession(String sessionToken, String csrfToken) {
    }
}
