package dev.inboxbridge.service.user;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.dto.DestinationMailboxFolderOptionsView;
import dev.inboxbridge.dto.EmailAccountConnectionTestResult;
import dev.inboxbridge.dto.UpdateUserMailDestinationRequest;
import dev.inboxbridge.dto.UserGmailConfigView;
import dev.inboxbridge.dto.UserMailDestinationView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserMailDestinationConfig;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import dev.inboxbridge.service.security.SecretEncryptionService;
import dev.inboxbridge.service.destination.ImapAppendMailDestinationService;
import dev.inboxbridge.service.destination.MailboxConflictService;
import dev.inboxbridge.service.mail.SourceTransportSecurityService;
import dev.inboxbridge.service.oauth.GoogleOAuthService;
import dev.inboxbridge.service.oauth.MicrosoftOAuthService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.oauth.UserGmailConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserMailDestinationConfigService {

    public static final String PROVIDER_GMAIL = "GMAIL_API";
    public static final String PROVIDER_OUTLOOK = "OUTLOOK_IMAP";
    public static final String PROVIDER_YAHOO = "YAHOO_IMAP";
    public static final String PROVIDER_PROTON_BRIDGE = "PROTON_BRIDGE_IMAP";
    public static final String PROVIDER_CUSTOM = "CUSTOM_IMAP";

    @Inject
    UserMailDestinationConfigRepository repository;

    @Inject
    UserGmailConfigService userGmailConfigService;

    @Inject
    SecretEncryptionService secretEncryptionService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    ImapAppendMailDestinationService imapAppendMailDestinationService;

    @Inject
    MailboxConflictService mailboxConflictService;

    @Inject
    SourceTransportSecurityService sourceTransportSecurityService;

    public UserMailDestinationView viewForUser(Long userId) {
        UserMailDestinationConfig config = repository.findByUserId(userId).orElse(null);
        if (config == null || PROVIDER_GMAIL.equals(config.provider)) {
            boolean gmailLinked = userGmailConfigService.destinationLinked(userId);
            UserGmailConfigView gmailView = userGmailConfigService.viewForUser(userId)
                    .orElse(userGmailConfigService.defaultView(userId));
            return new UserMailDestinationView(
                    PROVIDER_GMAIL,
                    "GMAIL_API",
                    config != null || gmailLinked,
                gmailLinked,
                    false,
                    gmailLinked,
                    gmailView.sharedClientConfigured(),
                    systemOAuthAppSettingsService.microsoftClientConfigured(),
                    gmailView.defaultRedirectUri(),
                    inboxBridgeConfig.microsoft().redirectUri(),
                    null,
                    null,
                    true,
                    InboxBridgeConfig.AuthMethod.OAUTH2.name(),
                    InboxBridgeConfig.OAuthProvider.GOOGLE.name(),
                    null,
                    null);
        }

        boolean passwordConfigured = config.passwordCiphertext != null && config.passwordNonce != null;
        boolean oauthConnected = InboxBridgeConfig.AuthMethod.OAUTH2.name().equals(config.authMethod)
                && InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(config.oauthProvider)
                && microsoftOAuthService.destinationLinked(userId);

        return new UserMailDestinationView(
                config.provider,
                "IMAP_APPEND",
                true,
                passwordConfigured || oauthConnected,
                passwordConfigured,
                oauthConnected,
                userGmailConfigService.sharedGoogleClientConfigured(),
                systemOAuthAppSettingsService.microsoftClientConfigured(),
                userGmailConfigService.defaultRedirectUri(),
                inboxBridgeConfig.microsoft().redirectUri(),
                config.host,
                config.port,
                config.tls,
                config.authMethod,
                config.oauthProvider,
                config.username,
                config.folderName == null || config.folderName.isBlank() ? "INBOX" : config.folderName);
    }

    public Optional<MailDestinationTarget> resolveForUser(Long userId, String ownerUsername) {
        UserMailDestinationConfig config = repository.findByUserId(userId).orElse(null);
        if (config == null || PROVIDER_GMAIL.equals(config.provider)) {
            UserGmailConfigView gmailView = userGmailConfigService.viewForUser(userId)
                    .orElse(userGmailConfigService.defaultView(userId));
            Optional<GoogleOAuthService.GoogleOAuthProfile> profile = userGmailConfigService.linkedGoogleProfileForUser(userId);
            if (profile.isEmpty()) {
                return Optional.empty();
            }
            GoogleOAuthService.GoogleOAuthProfile googleProfile = profile.get();
            return Optional.of(new GmailApiDestinationTarget(
                    googleProfile.subjectKey(),
                    userId,
                    ownerUsername,
                    PROVIDER_GMAIL,
                    gmailView.destinationUser(),
                    googleProfile.clientId(),
                    googleProfile.clientSecret(),
                    googleProfile.refreshToken(),
                    googleProfile.redirectUri(),
                    gmailView.createMissingLabels(),
                    gmailView.neverMarkSpam(),
                    gmailView.processForCalendar()));
        }

        InboxBridgeConfig.AuthMethod authMethod = InboxBridgeConfig.AuthMethod.valueOf(config.authMethod);
        if (authMethod == InboxBridgeConfig.AuthMethod.PASSWORD && !secretEncryptionService.isConfigured()) {
            return Optional.empty();
        }
        String password = authMethod == InboxBridgeConfig.AuthMethod.PASSWORD
            ? decryptPassword(config)
            : null;
        return Optional.of(new ImapAppendDestinationTarget(
                "user-destination:" + userId,
                userId,
                ownerUsername,
                config.provider,
                requireNonBlank(config.host, "Destination host"),
                config.port == null ? 993 : config.port,
                config.tls,
                authMethod,
                InboxBridgeConfig.OAuthProvider.valueOf(config.oauthProvider),
                requireNonBlank(config.username, "Destination username"),
                password,
                config.folderName == null || config.folderName.isBlank() ? "INBOX" : config.folderName));
    }

    public boolean isAnyDestinationConfigured(Long userId) {
        return repository.findByUserId(userId).isPresent() || userGmailConfigService.destinationLinked(userId);
    }

    public DestinationMailboxFolderOptionsView listFoldersForUser(Long userId, String ownerUsername) {
        Optional<MailDestinationTarget> target = resolveForUser(userId, ownerUsername);
        if (target.isEmpty() || !(target.get() instanceof ImapAppendDestinationTarget imapTarget)) {
            throw new IllegalStateException("Save and connect an IMAP destination mailbox before loading folders.");
        }
        if (!imapAppendMailDestinationService.isLinked(imapTarget)) {
            throw new IllegalStateException("Save and connect the destination mailbox before loading folders.");
        }

        List<String> folders = imapAppendMailDestinationService.listFolders(imapTarget);
        return new DestinationMailboxFolderOptionsView(folders);
    }

    public DestinationMailboxFolderOptionsView listFoldersForUser(AppUser user, UpdateUserMailDestinationRequest request) {
        ImapAppendDestinationTarget target = previewImapTarget(user, request);
        ImapAppendDestinationTarget effectiveTarget = upgradeTargetToTlsIfRecommended(target).orElse(target);
        if (effectiveTarget.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && !imapAppendMailDestinationService.isLinked(effectiveTarget)) {
            throw new IllegalStateException("Save and connect the destination mailbox before loading folders.");
        }
        return new DestinationMailboxFolderOptionsView(imapAppendMailDestinationService.listFolders(effectiveTarget));
    }

    public EmailAccountConnectionTestResult testConnectionForUser(AppUser user, UpdateUserMailDestinationRequest request) {
        ImapAppendDestinationTarget target = previewImapTarget(user, request);
        Integer recommendedTlsPort = sourceTransportSecurityService.detectRecommendedTlsPort(
                InboxBridgeConfig.Protocol.IMAP,
                target.host(),
                target.port()).orElse(null);
        ImapAppendDestinationTarget effectiveTarget = !target.tls() && recommendedTlsPort != null
                ? new ImapAppendDestinationTarget(
                        target.subjectKey(),
                        target.userId(),
                        target.ownerUsername(),
                        target.providerId(),
                        target.host(),
                        recommendedTlsPort,
                        true,
                        target.authMethod(),
                        target.oauthProvider(),
                        target.username(),
                        target.password(),
                        target.folder())
                : target;
        if (effectiveTarget.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && !imapAppendMailDestinationService.isLinked(effectiveTarget)) {
            throw new IllegalStateException("Save and connect the destination mailbox before testing it.");
        }
        EmailAccountConnectionTestResult result = imapAppendMailDestinationService.testConnection(effectiveTarget);
        return new EmailAccountConnectionTestResult(
                result.success(),
                effectiveTarget == target
                        ? result.message()
                        : "Connection test succeeded over TLS. InboxBridge verified a secure IMAP endpoint on port "
                                + effectiveTarget.port() + " and switched this destination to TLS automatically.",
                result.protocol(),
                result.host(),
                result.port(),
                result.tls(),
                result.authMethod(),
                result.oauthProvider(),
                result.authenticated(),
                result.folder(),
                result.folderAccessible(),
                result.unreadFilterRequested(),
                result.unreadFilterSupported(),
                result.unreadFilterValidated(),
                result.visibleMessageCount(),
                result.unreadMessageCount(),
                result.sampleMessageAvailable(),
                result.sampleMessageMaterialized(),
                result.forwardedMarkerSupported(),
                recommendedTlsPort != null,
                recommendedTlsPort != null,
                recommendedTlsPort,
                effectiveTarget == target
                        ? result.transportSecurityWarning()
                        : "Connection test succeeded over TLS. InboxBridge verified a secure IMAP endpoint on port "
                                + effectiveTarget.port() + " and switched this destination to TLS automatically.");
    }

    @Transactional
    public UserMailDestinationView update(AppUser user, UpdateUserMailDestinationRequest request) {
        String provider = normalizeProvider(request.provider());
        UserMailDestinationConfig config = repository.findByUserId(user.id).orElseGet(UserMailDestinationConfig::new);
        String nextAuthMethod = PROVIDER_GMAIL.equals(provider)
            ? InboxBridgeConfig.AuthMethod.OAUTH2.name()
            : PROVIDER_OUTLOOK.equals(provider)
                ? InboxBridgeConfig.AuthMethod.OAUTH2.name()
                : parseAuthMethod(request.authMethod()).name();
        String nextOAuthProvider = PROVIDER_GMAIL.equals(provider)
            ? InboxBridgeConfig.OAuthProvider.GOOGLE.name()
            : PROVIDER_OUTLOOK.equals(provider)
                ? InboxBridgeConfig.OAuthProvider.MICROSOFT.name()
                : parseOAuthProvider(request.oauthProvider()).name();
        unlinkReplacedOAuthDestination(user.id, config, provider, nextAuthMethod, nextOAuthProvider);
        config.userId = user.id;
        config.provider = provider;
        config.updatedAt = Instant.now();
        config.keyVersion = secretEncryptionService.isConfigured() ? secretEncryptionService.keyVersion() : config.keyVersion;

        if (PROVIDER_GMAIL.equals(provider)) {
            clearImapFields(config);
            repository.persist(config);
            mailboxConflictService.disableSourcesMatchingCurrentDestination(user.id);
            return viewForUser(user.id);
        }

        config.host = requireNonBlank(request.host(), "Destination host");
        config.port = request.port() == null ? 993 : request.port();
        config.tls = request.tls() == null || request.tls();
        config.authMethod = nextAuthMethod;
        config.oauthProvider = nextOAuthProvider;
        config.folderName = blankToDefault(request.folder(), "INBOX");

        if (InboxBridgeConfig.AuthMethod.PASSWORD.name().equals(config.authMethod)) {
            config.username = requireNonBlank(request.username(), "Destination username");
            if (!secretEncryptionService.isConfigured()) {
                throw new IllegalStateException("Secure secret storage must be configured before storing destination mailbox passwords in the database.");
            }
            setPassword(config, request.password());
            if (config.passwordCiphertext == null || config.passwordNonce == null) {
                throw new IllegalArgumentException("Destination mailbox password is required");
            }
        } else {
            config.passwordCiphertext = null;
            config.passwordNonce = null;
            if (!InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(config.oauthProvider)) {
                throw new IllegalArgumentException("Only Microsoft OAuth2 is currently supported for IMAP destination mailboxes.");
            }
            config.username = requireNonBlank(request.username(), "Destination username");
        }

        enforceDestinationTransportSecurity(config);
        repository.persist(config);
        mailboxConflictService.disableSourcesMatchingCurrentDestination(user.id);
        return viewForUser(user.id);
    }

    @Transactional
    public DestinationUnlinkResult unlinkForUser(Long userId) {
        UserMailDestinationConfig config = repository.findByUserId(userId).orElse(null);
        if (config == null || PROVIDER_GMAIL.equals(config.provider)) {
            UserGmailConfigService.GmailUnlinkResult result = userGmailConfigService.unlinkForUser(userId);
            return new DestinationUnlinkResult(result.providerRevocationAttempted(), result.providerRevoked());
        }
        if (InboxBridgeConfig.AuthMethod.OAUTH2.name().equals(config.authMethod)
                && InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(config.oauthProvider)) {
            microsoftOAuthService.unlinkDestination(userId);
        }
        return new DestinationUnlinkResult(false, false);
    }

    private void clearImapFields(UserMailDestinationConfig config) {
        config.host = null;
        config.port = null;
        config.tls = true;
        config.authMethod = InboxBridgeConfig.AuthMethod.OAUTH2.name();
        config.oauthProvider = InboxBridgeConfig.OAuthProvider.GOOGLE.name();
        config.username = null;
        config.passwordCiphertext = null;
        config.passwordNonce = null;
        config.folderName = null;
    }

    private void unlinkReplacedOAuthDestination(
            Long userId,
            UserMailDestinationConfig existingConfig,
            String nextProvider,
            String nextAuthMethod,
            String nextOAuthProvider) {
        String currentLinkedProvider = linkedOauthProvider(userId, existingConfig);
        if (currentLinkedProvider == null) {
            return;
        }
        String requestedLinkedProvider = requestedOauthProvider(nextProvider, nextAuthMethod, nextOAuthProvider);
        if (Objects.equals(currentLinkedProvider, requestedLinkedProvider)) {
            return;
        }
        if (InboxBridgeConfig.OAuthProvider.GOOGLE.name().equals(currentLinkedProvider)) {
            userGmailConfigService.unlinkForUser(userId);
            return;
        }
        microsoftOAuthService.unlinkDestination(userId);
    }

    private String linkedOauthProvider(Long userId, UserMailDestinationConfig existingConfig) {
        if (existingConfig == null || PROVIDER_GMAIL.equals(existingConfig.provider)) {
            return userGmailConfigService.destinationLinked(userId)
                    ? InboxBridgeConfig.OAuthProvider.GOOGLE.name()
                    : null;
        }
        if (InboxBridgeConfig.AuthMethod.OAUTH2.name().equals(existingConfig.authMethod)
                && InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(existingConfig.oauthProvider)
                && microsoftOAuthService.destinationLinked(userId)) {
            return InboxBridgeConfig.OAuthProvider.MICROSOFT.name();
        }
        return null;
    }

    private String requestedOauthProvider(String provider, String authMethod, String oauthProvider) {
        if (PROVIDER_GMAIL.equals(provider)) {
            return InboxBridgeConfig.OAuthProvider.GOOGLE.name();
        }
        if (InboxBridgeConfig.AuthMethod.OAUTH2.name().equals(authMethod)
                && InboxBridgeConfig.OAuthProvider.MICROSOFT.name().equals(oauthProvider)) {
            return InboxBridgeConfig.OAuthProvider.MICROSOFT.name();
        }
        return null;
    }

    private void setPassword(UserMailDestinationConfig config, String password) {
        if (password == null || password.isBlank()) {
            return;
        }
        SecretEncryptionService.EncryptedValue encrypted = secretEncryptionService.encrypt(password, "user-destination:" + config.userId + ":password");
        config.passwordCiphertext = encrypted.ciphertextBase64();
        config.passwordNonce = encrypted.nonceBase64();
    }

    private String decryptPassword(UserMailDestinationConfig config) {
        if (config.passwordCiphertext == null || config.passwordNonce == null) {
            return "";
        }
        return secretEncryptionService.decrypt(
                config.passwordCiphertext,
                config.passwordNonce,
                config.keyVersion,
                "user-destination:" + config.userId + ":password");
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return PROVIDER_GMAIL;
        }
        String normalized = provider.trim().toUpperCase();
        return switch (normalized) {
            case PROVIDER_GMAIL, PROVIDER_OUTLOOK, PROVIDER_YAHOO, PROVIDER_PROTON_BRIDGE, PROVIDER_CUSTOM -> normalized;
            default -> throw new IllegalArgumentException("Unsupported destination provider: " + provider);
        };
    }

    private InboxBridgeConfig.AuthMethod parseAuthMethod(String authMethod) {
        if (authMethod == null || authMethod.isBlank()) {
            return InboxBridgeConfig.AuthMethod.PASSWORD;
        }
        return InboxBridgeConfig.AuthMethod.valueOf(authMethod.trim().toUpperCase());
    }

    private InboxBridgeConfig.OAuthProvider parseOAuthProvider(String oauthProvider) {
        if (oauthProvider == null || oauthProvider.isBlank()) {
            return InboxBridgeConfig.OAuthProvider.NONE;
        }
        return InboxBridgeConfig.OAuthProvider.valueOf(oauthProvider.trim().toUpperCase());
    }

    private String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private ImapAppendDestinationTarget previewImapTarget(AppUser user, UpdateUserMailDestinationRequest request) {
        String provider = normalizeProvider(request.provider());
        if (PROVIDER_GMAIL.equals(provider)) {
            throw new IllegalArgumentException("Gmail destinations do not use IMAP connection testing.");
        }

        InboxBridgeConfig.AuthMethod authMethod = PROVIDER_OUTLOOK.equals(provider)
                ? InboxBridgeConfig.AuthMethod.OAUTH2
                : parseAuthMethod(request.authMethod());
        InboxBridgeConfig.OAuthProvider oauthProvider = PROVIDER_OUTLOOK.equals(provider)
                ? InboxBridgeConfig.OAuthProvider.MICROSOFT
                : parseOAuthProvider(request.oauthProvider());
        String password = null;
        if (authMethod == InboxBridgeConfig.AuthMethod.PASSWORD) {
            password = previewPassword(user.id, request.password());
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("Destination mailbox password is required");
            }
        } else if (oauthProvider != InboxBridgeConfig.OAuthProvider.MICROSOFT) {
            throw new IllegalArgumentException("Only Microsoft OAuth2 is currently supported for IMAP destination mailboxes.");
        }

        return new ImapAppendDestinationTarget(
                "user-destination-preview:" + user.id,
                user.id,
                user.username,
                provider,
                requireNonBlank(request.host(), "Destination host"),
                request.port() == null ? 993 : request.port(),
                request.tls() == null || request.tls(),
                authMethod,
                oauthProvider,
                requireNonBlank(request.username(), "Destination username"),
                password,
                blankToDefault(request.folder(), "INBOX"));
    }

    private Optional<ImapAppendDestinationTarget> upgradeTargetToTlsIfRecommended(ImapAppendDestinationTarget target) {
        if (target == null || target.tls()) {
            return Optional.empty();
        }
        return sourceTransportSecurityService.detectRecommendedTlsPort(
                        InboxBridgeConfig.Protocol.IMAP,
                        target.host(),
                        target.port())
                .map(securePort -> new ImapAppendDestinationTarget(
                        target.subjectKey(),
                        target.userId(),
                        target.ownerUsername(),
                        target.providerId(),
                        target.host(),
                        securePort,
                        true,
                        target.authMethod(),
                        target.oauthProvider(),
                        target.username(),
                        target.password(),
                        target.folder()));
    }

    private void enforceDestinationTransportSecurity(UserMailDestinationConfig config) {
        if (config == null || config.tls || config.host == null || config.host.isBlank()) {
            return;
        }
        sourceTransportSecurityService.detectRecommendedTlsPort(
                        InboxBridgeConfig.Protocol.IMAP,
                        config.host,
                        config.port)
                .ifPresent(securePort -> {
                    throw new IllegalArgumentException(
                            sourceTransportSecurityService.insecureDestinationConnectionMessage(securePort));
                });
    }

    private String previewPassword(Long userId, String requestPassword) {
        if (requestPassword != null && !requestPassword.isBlank()) {
            return requestPassword;
        }
        if (!secretEncryptionService.isConfigured()) {
            return null;
        }
        UserMailDestinationConfig existing = repository.findByUserId(userId).orElse(null);
        if (existing == null) {
            return null;
        }
        return decryptPassword(existing);
    }

    public record DestinationUnlinkResult(
            boolean providerRevocationAttempted,
            boolean providerRevoked) {
    }
}
