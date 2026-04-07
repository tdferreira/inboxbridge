package dev.inboxbridge.service.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollAction;
import dev.inboxbridge.domain.SourcePostPollSettings;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.service.EnvSourceService;
import dev.inboxbridge.service.oauth.OAuthCredentialService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RuntimeEmailAccountService {

    @Inject
    InboxBridgeConfig config;

    @Inject
    UserEmailAccountService userEmailAccountService;

    @Inject
    UserMailDestinationConfigService userMailDestinationConfigService;

    @Inject
    AppUserRepository appUserRepository;

    @Inject
    EnvSourceService envSourceService;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Transactional
    public Optional<RuntimeEmailAccount> findSystemBridge(String sourceId) {
        return envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .filter(source -> source.id().equals(sourceId))
                .findFirst()
                .map(source -> toRuntimeEmailAccount(source, systemDestinationTarget()));
    }

    @Transactional
    public Optional<RuntimeEmailAccount> findAccessibleForUser(AppUser actor, String sourceId) {
        // User-scoped endpoints must only target DB-managed fetchers. Env-managed
        // sources are exposed through the admin endpoints to avoid ID collisions.
        Optional<UserEmailAccount> emailAccount = userEmailAccountService.findByEmailAccountId(sourceId);
        if (emailAccount.isEmpty() || !emailAccount.get().userId.equals(actor.id)) {
            return Optional.empty();
        }
        return toRuntimeEmailAccount(emailAccount.get());
    }

    @Transactional
    public Optional<RuntimeEmailAccount> findUserManagedById(String sourceId) {
        return userEmailAccountService.findByEmailAccountId(sourceId)
                .flatMap(this::toRuntimeEmailAccount);
    }

    @Transactional
    public Optional<RuntimeEmailAccount> findAnyAccessibleForAdmin(String sourceId) {
        Optional<RuntimeEmailAccount> systemSource = findSystemBridge(sourceId);
        if (systemSource.isPresent()) {
            return systemSource;
        }
        return findUserManagedById(sourceId);
    }

    @Transactional
    public List<RuntimeEmailAccount> listEnabledForPolling() {
        List<RuntimeEmailAccount> emailAccounts = new ArrayList<>();
        MailDestinationTarget systemTarget = systemDestinationTarget();

        for (EnvSourceService.IndexedSource indexedSource : envSourceService.configuredSources()) {
            InboxBridgeConfig.Source source = indexedSource.source();
            if (!source.enabled()) {
                continue;
            }
            emailAccounts.add(toRuntimeEmailAccount(source, systemTarget));
        }

        for (UserEmailAccount emailAccount : userEmailAccountService.listEnabledBridges()) {
            toRuntimeEmailAccount(emailAccount).ifPresent(emailAccounts::add);
        }
        return emailAccounts;
    }

    @Transactional
    public List<RuntimeEmailAccount> listEnabledForUser(AppUser actor) {
        List<RuntimeEmailAccount> emailAccounts = new ArrayList<>();
        for (UserEmailAccount emailAccount : userEmailAccountService.listEnabledBridges()) {
            if (!emailAccount.userId.equals(actor.id)) {
                continue;
            }
            toRuntimeEmailAccount(emailAccount).ifPresent(emailAccounts::add);
        }
        return emailAccounts;
    }

    private MailDestinationTarget systemDestinationTarget() {
        return new GmailApiDestinationTarget(
                "gmail-destination",
                null,
                "system",
                UserMailDestinationConfigService.PROVIDER_GMAIL,
                systemOAuthAppSettingsService.googleDestinationUser(),
                systemOAuthAppSettingsService.googleClientId(),
                systemOAuthAppSettingsService.googleClientSecret(),
                systemOAuthAppSettingsService.googleRefreshToken(),
                systemOAuthAppSettingsService.googleRedirectUri(),
                config.gmail().createMissingLabels(),
                config.gmail().neverMarkSpam(),
                config.gmail().processForCalendar());
    }

    private RuntimeEmailAccount toRuntimeEmailAccount(InboxBridgeConfig.Source source, MailDestinationTarget destinationTarget) {
        return new RuntimeEmailAccount(
                source.id(),
                "SYSTEM",
                null,
                "system",
                source.enabled(),
                source.protocol(),
                source.host(),
                source.port(),
                source.tls(),
                source.authMethod(),
                source.oauthProvider(),
                source.username(),
                source.password(),
                source.oauthRefreshToken().orElse(""),
                source.folder(),
                source.unreadOnly(),
                source.fetchMode(),
                source.customLabel(),
                SourcePostPollSettings.none(),
                destinationTarget);
    }

    private Optional<RuntimeEmailAccount> toRuntimeEmailAccount(UserEmailAccount emailAccount) {
        Optional<AppUser> owner = appUserRepository.findByIdOptional(emailAccount.userId);
        if (owner.isEmpty() || !owner.get().active || !owner.get().approved) {
            return Optional.empty();
        }
        Optional<MailDestinationTarget> destinationTarget = userMailDestinationConfigService.resolveForUser(emailAccount.userId, owner.get().username);
        if (destinationTarget.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RuntimeEmailAccount(
                emailAccount.emailAccountId,
                "USER",
                emailAccount.userId,
                owner.get().username,
                emailAccount.enabled,
                emailAccount.protocol,
                emailAccount.host,
                emailAccount.port,
                emailAccount.tls,
                emailAccount.authMethod,
                emailAccount.oauthProvider,
                emailAccount.username,
                userEmailAccountService.decryptPassword(emailAccount),
                userEmailAccountService.decryptRefreshToken(emailAccount),
                Optional.ofNullable(emailAccount.folderName),
                emailAccount.unreadOnly,
                emailAccount.fetchMode == null ? SourceFetchMode.POLLING : emailAccount.fetchMode,
                Optional.ofNullable(emailAccount.customLabel),
                new SourcePostPollSettings(
                        emailAccount.markReadAfterPoll,
                        emailAccount.postPollAction == null ? SourcePostPollAction.NONE : emailAccount.postPollAction,
                        Optional.ofNullable(emailAccount.postPollTargetFolder)),
                destinationTarget.get()));
    }
}
