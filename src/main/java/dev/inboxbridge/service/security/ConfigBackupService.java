package dev.inboxbridge.service.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.inboxbridge.dto.ConfigBackupExportRequest;
import dev.inboxbridge.dto.EncryptedConfigBackupView;
import dev.inboxbridge.dto.SafeConfigBackupView;
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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ConfigBackupService {

    private static final String FULL_EXPORT_WARNING =
            "This encrypted export contains mailbox passwords, OAuth client secrets, and refresh tokens. Anyone who decrypts the file can access configured mailboxes.";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AppUserRepository appUserRepository;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    UserMailDestinationConfigRepository userMailDestinationConfigRepository;

    @Inject
    UserGmailConfigRepository userGmailConfigRepository;

    @Inject
    OAuthCredentialRepository oAuthCredentialRepository;

    @Inject
    ConfigBackupExportAuditRepository auditRepository;

    @Inject
    SecretEncryptionService secretEncryptionService;

    @Inject
    SecretManagementService secretManagementService;

    @Transactional
    public SafeConfigBackupView safeBackup(AppUser actor) {
        JsonNode snapshot = objectMapper.valueToTree(snapshot(false));
        audit(actor, "SAFE_REDACTED", false, null);
        return new SafeConfigBackupView(
                Instant.now(),
                false,
                "Secrets are redacted. This backup is safe for configuration review but cannot restore mailbox access.",
                snapshot);
    }

    @Transactional
    public EncryptedConfigBackupView encryptedSecretsBackup(
            AppUser actor,
            UserSession currentSession,
            ConfigBackupExportRequest request) {
        if (request == null || request.riskAcknowledged() == null || !request.riskAcknowledged()) {
            throw new IllegalArgumentException("Confirm that the encrypted export can grant mailbox access to anyone who can decrypt it.");
        }
        SecretManagementStatusView status = secretManagementService.status(currentSession);
        if (status.reauthenticationRequired() && !status.reauthenticationSatisfied()) {
            throw new IllegalStateException("Fresh passkey or password reauthentication is required before exporting secrets.");
        }
        RSAPublicKey publicKey = parsePublicKey(request.publicKeyPem());
        String fingerprint = fingerprint(publicKey);
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(snapshot(true));
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            SecretKey dataKey = keyGenerator.generateKey();
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);

            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = aes.doFinal(plaintext);

            Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            rsa.init(Cipher.ENCRYPT_MODE, publicKey, oaepSha256Spec());
            byte[] encryptedKey = rsa.doFinal(dataKey.getEncoded());

            audit(actor, "ENCRYPTED_SECRETS", true, fingerprint);
            return new EncryptedConfigBackupView(
                    Instant.now(),
                    "RSA-OAEP-SHA256 + AES-256-GCM",
                    fingerprint,
                    Base64.getEncoder().encodeToString(encryptedKey),
                    Base64.getEncoder().encodeToString(nonce),
                    Base64.getEncoder().encodeToString(ciphertext),
                    FULL_EXPORT_WARNING);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt configuration backup", e);
        }
    }

    private Map<String, Object> snapshot(boolean includeSecrets) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", "inboxbridge-config-backup-v1");
        root.put("generatedAt", Instant.now().toString());
        root.put("includesSecrets", includeSecrets);
        root.put("users", appUserRepository.listAll().stream().map(this::userView).toList());
        root.put("destinationConfigs", userMailDestinationConfigRepository.listAll().stream()
                .map(config -> destinationView(config, includeSecrets)).toList());
        root.put("sourceMailboxes", userEmailAccountRepository.listAll().stream()
                .map(source -> sourceView(source, includeSecrets)).toList());
        root.put("gmailConfigs", userGmailConfigRepository.listAll().stream()
                .map(config -> gmailView(config, includeSecrets)).toList());
        root.put("oauthCredentials", oAuthCredentialRepository.listAll().stream()
                .map(credential -> oauthCredentialView(credential, includeSecrets)).toList());
        return root;
    }

    private Map<String, Object> userView(AppUser user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.id);
        view.put("username", user.username);
        view.put("role", user.role == null ? null : user.role.name());
        view.put("active", user.active);
        view.put("approved", user.approved);
        return view;
    }

    private Map<String, Object> destinationView(UserMailDestinationConfig config, boolean includeSecrets) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userId", config.userId);
        view.put("provider", config.provider);
        view.put("host", config.host);
        view.put("port", config.port);
        view.put("tls", config.tls);
        view.put("authMethod", config.authMethod);
        view.put("oauthProvider", config.oauthProvider);
        view.put("username", config.username);
        view.put("folder", config.folderName);
        view.put("spamJunkFolder", config.spamJunkFolderName);
        putSecret(view, "password", includeSecrets, config.passwordCiphertext, config.passwordNonce, config.keyVersion,
                "user-destination:" + config.userId + ":password");
        return view;
    }

    private Map<String, Object> sourceView(UserEmailAccount source, boolean includeSecrets) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userId", source.userId);
        view.put("emailAccountId", source.emailAccountId);
        view.put("enabled", source.enabled);
        view.put("protocol", source.protocol == null ? null : source.protocol.name());
        view.put("host", source.host);
        view.put("port", source.port);
        view.put("tls", source.tls);
        view.put("authMethod", source.authMethod == null ? null : source.authMethod.name());
        view.put("oauthProvider", source.oauthProvider == null ? null : source.oauthProvider.name());
        view.put("username", source.username);
        view.put("folder", source.folderName);
        view.put("unreadOnly", source.unreadOnly);
        view.put("fetchMode", source.fetchMode == null ? null : source.fetchMode.name());
        view.put("customLabel", source.customLabel);
        view.put("folderLabelMappings", source.folderLabelMappings);
        view.put("spamJunkStrategy", source.spamJunkStrategy == null ? null : source.spamJunkStrategy.name());
        view.put("spamJunkSourceFolder", source.spamJunkSourceFolder);
        putSecret(view, "password", includeSecrets, source.passwordCiphertext, source.passwordNonce, source.keyVersion,
                "user-bridge:" + source.userId + ":" + source.emailAccountId + ":password");
        putSecret(view, "oauthRefreshToken", includeSecrets, source.oauthRefreshTokenCiphertext, source.oauthRefreshTokenNonce, source.keyVersion,
                "user-bridge:" + source.userId + ":" + source.emailAccountId + ":oauth-refresh-token");
        return view;
    }

    private Map<String, Object> gmailView(UserGmailConfig config, boolean includeSecrets) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userId", config.userId);
        view.put("destinationUser", config.destinationUser);
        view.put("linkedMailboxAddress", config.linkedMailboxAddress);
        view.put("redirectUri", config.redirectUri);
        view.put("createMissingLabels", config.createMissingLabels);
        view.put("neverMarkSpam", config.neverMarkSpam);
        view.put("processForCalendar", config.processForCalendar);
        putSecret(view, "clientId", includeSecrets, config.clientIdCiphertext, config.clientIdNonce, config.keyVersion,
                "user-gmail:" + config.userId + ":client-id");
        putSecret(view, "clientSecret", includeSecrets, config.clientSecretCiphertext, config.clientSecretNonce, config.keyVersion,
                "user-gmail:" + config.userId + ":client-secret");
        putSecret(view, "refreshToken", includeSecrets, config.refreshTokenCiphertext, config.refreshTokenNonce, config.keyVersion,
                "user-gmail:" + config.userId + ":refresh-token");
        return view;
    }

    private Map<String, Object> oauthCredentialView(OAuthCredential credential, boolean includeSecrets) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("provider", credential.provider);
        view.put("subjectKey", credential.subjectKey);
        view.put("accessExpiresAt", credential.accessExpiresAt);
        view.put("tokenScope", credential.tokenScope);
        view.put("tokenType", credential.tokenType);
        putSecret(view, "refreshToken", includeSecrets, credential.refreshTokenCiphertext, credential.refreshTokenNonce, credential.keyVersion,
                credential.provider + ":" + credential.subjectKey + ":refresh");
        putSecret(view, "accessToken", includeSecrets, credential.accessTokenCiphertext, credential.accessTokenNonce, credential.keyVersion,
                credential.provider + ":" + credential.subjectKey + ":access");
        return view;
    }

    private void putSecret(
            Map<String, Object> view,
            String name,
            boolean includeSecrets,
            String ciphertext,
            String nonce,
            String keyVersion,
            String context) {
        boolean configured = ciphertext != null && nonce != null;
        if (!includeSecrets) {
            view.put(name + "Configured", configured);
            return;
        }
        view.put(name, configured ? secretEncryptionService.decrypt(ciphertext, nonce, keyVersion, context) : null);
    }

    private RSAPublicKey parsePublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("An operator-provided RSA public key is required.");
        }
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] encoded = Base64.getDecoder().decode(normalized);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException e) {
            throw new IllegalArgumentException("The backup public key must be an RSA X.509 PEM public key.", e);
        }
    }

    private String fingerprint(RSAPublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to fingerprint backup public key", e);
        }
    }

    private OAEPParameterSpec oaepSha256Spec() {
        return new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
    }

    private void audit(AppUser actor, String exportType, boolean encrypted, String publicKeyFingerprint) {
        ConfigBackupExportAudit audit = new ConfigBackupExportAudit();
        audit.actorUserId = actor.id;
        audit.actorUsername = actor.username;
        audit.exportType = exportType;
        audit.encrypted = encrypted;
        audit.publicKeyFingerprint = publicKeyFingerprint;
        audit.createdAt = Instant.now();
        auditRepository.persist(audit);
    }
}
