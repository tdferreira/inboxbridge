package dev.inboxbridge.service.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.MGF1ParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.ConfigBackupExportRequest;
import dev.inboxbridge.dto.SecretManagementStatusView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.ConfigBackupExportAudit;
import dev.inboxbridge.persistence.ConfigBackupExportAuditRepository;
import dev.inboxbridge.persistence.OAuthCredential;
import dev.inboxbridge.persistence.OAuthCredentialRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfig;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import dev.inboxbridge.persistence.UserSession;

class ConfigBackupServiceTest {

    @Test
    void safeBackupRedactsSecretsAndAuditsExport() {
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        ConfigBackupService service = configuredService(auditRepository, true, true);

        var backup = service.safeBackup(admin());

        assertFalse(backup.includesSecrets());
        assertFalse(backup.data().path("includesSecrets").asBoolean());
        JsonNode source = backup.data().path("sourceMailboxes").get(0);
        assertTrue(source.path("passwordConfigured").asBoolean());
        assertFalse(source.has("password"));
        assertEquals("SAFE_REDACTED", auditRepository.values.get(0).exportType);
        assertFalse(auditRepository.values.get(0).encrypted);
    }

    @Test
    void encryptedSecretsBackupRequiresRiskAcknowledgement() {
        ConfigBackupService service = configuredService(new InMemoryAuditRepository(), true, true);

        assertThrows(IllegalArgumentException.class, () ->
                service.encryptedSecretsBackup(admin(), session(), new ConfigBackupExportRequest("key", false)));
    }

    @Test
    void encryptedSecretsBackupRequiresFreshSensitiveActionAuthentication() throws Exception {
        ConfigBackupService service = configuredService(new InMemoryAuditRepository(), true, false);
        String publicKey = publicKeyPem(KeyPairGenerator.getInstance("RSA").generateKeyPair());

        assertThrows(IllegalStateException.class, () ->
                service.encryptedSecretsBackup(admin(), session(), new ConfigBackupExportRequest(publicKey, true)));
    }

    @Test
    void encryptedSecretsBackupEncryptsSecretSnapshotAndAuditsMetadataOnly() throws Exception {
        InMemoryAuditRepository auditRepository = new InMemoryAuditRepository();
        ConfigBackupService service = configuredService(auditRepository, true, true);
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();

        var backup = service.encryptedSecretsBackup(
                admin(),
                session(),
                new ConfigBackupExportRequest(publicKeyPem(keyPair), true));

        assertEquals("RSA-OAEP-SHA256 + AES-256-GCM", backup.algorithm());
        assertTrue(backup.warning().contains("Anyone who decrypts the file can access configured mailboxes"));
        assertEquals(fingerprint(keyPair), backup.publicKeyFingerprint());
        assertEquals("ENCRYPTED_SECRETS", auditRepository.values.get(0).exportType);
        assertTrue(auditRepository.values.get(0).encrypted);
        assertEquals(backup.publicKeyFingerprint(), auditRepository.values.get(0).publicKeyFingerprint);

        JsonNode plaintext = decryptBackup(keyPair, backup.encryptedKey(), backup.nonce(), backup.ciphertext());
        assertTrue(plaintext.path("includesSecrets").asBoolean());
        assertEquals("decrypted:user-bridge:4:source-a:password",
                plaintext.path("sourceMailboxes").get(0).path("password").asText());
        assertEquals("decrypted:user-destination:4:password",
                plaintext.path("destinationConfigs").get(0).path("password").asText());
        assertEquals("decrypted:user-gmail:4:client-secret",
                plaintext.path("gmailConfigs").get(0).path("clientSecret").asText());
        assertEquals("decrypted:GOOGLE:gmail-destination:refresh",
                plaintext.path("oauthCredentials").get(0).path("refreshToken").asText());
    }

    private static ConfigBackupService configuredService(
            InMemoryAuditRepository auditRepository,
            boolean reauthenticationRequired,
            boolean reauthenticationSatisfied) {
        ConfigBackupService service = new ConfigBackupService();
        service.objectMapper = new ObjectMapper();
        service.appUserRepository = new InMemoryAppUserRepository(List.of(admin()));
        service.userEmailAccountRepository = new InMemoryUserEmailAccountRepository(List.of(sourceAccount()));
        service.userMailDestinationConfigRepository = new InMemoryUserMailDestinationConfigRepository(List.of(destination()));
        service.userGmailConfigRepository = new InMemoryUserGmailConfigRepository(List.of(gmailConfig()));
        service.oAuthCredentialRepository = new InMemoryOAuthCredentialRepository(List.of(oauthCredential()));
        service.auditRepository = auditRepository;
        service.secretEncryptionService = new RecordingSecretEncryptionService();
        service.secretManagementService = new FixedSecretManagementService(reauthenticationRequired, reauthenticationSatisfied);
        return service;
    }

    private static AppUser admin() {
        AppUser user = new AppUser();
        user.id = 4L;
        user.username = "admin";
        user.role = AppUser.Role.ADMIN;
        user.active = true;
        user.approved = true;
        return user;
    }

    private static UserSession session() {
        UserSession session = new UserSession();
        session.id = 11L;
        session.userId = admin().id;
        session.lastSensitiveAuthAt = Instant.now();
        return session;
    }

    private static UserEmailAccount sourceAccount() {
        UserEmailAccount source = new UserEmailAccount();
        source.userId = 4L;
        source.emailAccountId = "source-a";
        source.host = "imap.example.com";
        source.username = "source@example.com";
        source.passwordCiphertext = "ciphertext";
        source.passwordNonce = "nonce";
        source.keyVersion = "v1";
        source.folderLabelMappings = "Archive=Imported/Archive";
        return source;
    }

    private static UserMailDestinationConfig destination() {
        UserMailDestinationConfig destination = new UserMailDestinationConfig();
        destination.userId = 4L;
        destination.host = "imap.destination.example.com";
        destination.username = "destination@example.com";
        destination.passwordCiphertext = "ciphertext";
        destination.passwordNonce = "nonce";
        destination.keyVersion = "v1";
        destination.spamJunkFolderName = "Junk";
        return destination;
    }

    private static UserGmailConfig gmailConfig() {
        UserGmailConfig config = new UserGmailConfig();
        config.userId = 4L;
        config.destinationUser = "me";
        config.clientSecretCiphertext = "ciphertext";
        config.clientSecretNonce = "nonce";
        config.keyVersion = "v1";
        return config;
    }

    private static OAuthCredential oauthCredential() {
        OAuthCredential credential = new OAuthCredential();
        credential.provider = "GOOGLE";
        credential.subjectKey = "gmail-destination";
        credential.refreshTokenCiphertext = "ciphertext";
        credential.refreshTokenNonce = "nonce";
        credential.keyVersion = "v1";
        return credential;
    }

    private static String publicKeyPem(KeyPair keyPair) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private static String fingerprint(KeyPair keyPair) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(keyPair.getPublic().getEncoded());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static JsonNode decryptBackup(
            KeyPair keyPair,
            String encryptedKey,
            String nonce,
            String ciphertext) throws Exception {
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        rsa.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT));
        byte[] dataKey = rsa.doFinal(Base64.getDecoder().decode(encryptedKey));

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(dataKey, "AES"),
                new GCMParameterSpec(128, Base64.getDecoder().decode(nonce)));
        byte[] plaintext = aes.doFinal(Base64.getDecoder().decode(ciphertext));
        return new ObjectMapper().readTree(plaintext);
    }

    private static SecretManagementStatusView status(boolean reauthenticationRequired, boolean reauthenticationSatisfied) {
        return new SecretManagementStatusView(
                true,
                "LOCAL",
                "local",
                true,
                true,
                "ready",
                List.of(),
                List.of(),
                "LOCAL:v1",
                "v1",
                List.of(),
                0,
                0,
                0,
                0,
                false,
                0,
                false,
                true,
                true,
                null,
                null,
                List.of(),
                false,
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                null,
                "PT0S",
                false,
                reauthenticationRequired,
                reauthenticationSatisfied,
                reauthenticationSatisfied ? Instant.now().plusSeconds(60) : null);
    }

    private static final class FixedSecretManagementService extends SecretManagementService {
        private final boolean reauthenticationRequired;
        private final boolean reauthenticationSatisfied;

        private FixedSecretManagementService(boolean reauthenticationRequired, boolean reauthenticationSatisfied) {
            this.reauthenticationRequired = reauthenticationRequired;
            this.reauthenticationSatisfied = reauthenticationSatisfied;
        }

        @Override
        public SecretManagementStatusView status(UserSession currentSession) {
            return ConfigBackupServiceTest.status(reauthenticationRequired, reauthenticationSatisfied);
        }
    }

    private static final class RecordingSecretEncryptionService extends SecretEncryptionService {
        @Override
        public String decrypt(String ciphertextBase64, String nonceBase64, String keyVersion, String context) {
            return "decrypted:" + context;
        }
    }

    private static final class InMemoryAppUserRepository extends AppUserRepository {
        private final List<AppUser> values;

        private InMemoryAppUserRepository(List<AppUser> values) {
            this.values = values;
        }

        @Override
        public List<AppUser> listAll() {
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

    private static final class InMemoryAuditRepository extends ConfigBackupExportAuditRepository {
        private final List<ConfigBackupExportAudit> values = new java.util.ArrayList<>();

        @Override
        public void persist(ConfigBackupExportAudit entity) {
            assertNotNull(entity.createdAt);
            values.add(entity);
        }
    }
}
