package dev.inboxbridge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

    public Optional<RuntimeEmailAccount> findSystemBridge(String sourceId) {
        return envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .filter(source -> source.id().equals(sourceId))
                .findFirst()
                .map(source -> toRuntimeEmailAccount(source, systemDestinationTarget()));
    }

    public Optional<RuntimeEmailAccount> findAccessibleForUser(AppUser actor, String sourceId) {
        // User-scoped endpoints must only target DB-managed fetchers. Env-managed
        // sources are exposed through the admin endpoints to avoid ID collisions.
        Optional<UserEmailAccount> bridge = userEmailAccountService.findByBridgeId(sourceId);
        if (bridge.isEmpty() || !bridge.get().userId.equals(actor.id)) {
            return Optional.empty();
        }
        return toRuntimeEmailAccount(bridge.get());
    }

    public List<RuntimeEmailAccount> listEnabledForPolling() {
        List<RuntimeEmailAccount> bridges = new ArrayList<>();
        MailDestinationTarget systemTarget = systemDestinationTarget();

        for (EnvSourceService.IndexedSource indexedSource : envSourceService.configuredSources()) {
            InboxBridgeConfig.Source source = indexedSource.source();
            if (!source.enabled()) {
                continue;
            }
            bridges.add(toRuntimeEmailAccount(source, systemTarget));
        }

        for (UserEmailAccount bridge : userEmailAccountService.listEnabledBridges()) {
            toRuntimeEmailAccount(bridge).ifPresent(bridges::add);
        }
        return bridges;
    }

    public List<RuntimeEmailAccount> listEnabledForUser(AppUser actor) {
        List<RuntimeEmailAccount> bridges = new ArrayList<>();
        for (UserEmailAccount bridge : userEmailAccountService.listEnabledBridges()) {
            if (!bridge.userId.equals(actor.id)) {
                continue;
            }
            toRuntimeEmailAccount(bridge).ifPresent(bridges::add);
        }
        return bridges;
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
                source.customLabel(),
                destinationTarget);
    }

    private Optional<RuntimeEmailAccount> toRuntimeEmailAccount(UserEmailAccount bridge) {
        Optional<AppUser> owner = appUserRepository.findByIdOptional(bridge.userId);
        if (owner.isEmpty() || !owner.get().active || !owner.get().approved) {
            return Optional.empty();
        }
        Optional<MailDestinationTarget> destinationTarget = userMailDestinationConfigService.resolveForUser(bridge.userId, owner.get().username);
        if (destinationTarget.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RuntimeEmailAccount(
                bridge.bridgeId,
                "USER",
                bridge.userId,
                owner.get().username,
                bridge.enabled,
                bridge.protocol,
                bridge.host,
                bridge.port,
                bridge.tls,
                bridge.authMethod,
                bridge.oauthProvider,
                bridge.username,
                userEmailAccountService.decryptPassword(bridge),
                userEmailAccountService.decryptRefreshToken(bridge),
                Optional.ofNullable(bridge.folderName),
                bridge.unreadOnly,
                Optional.ofNullable(bridge.customLabel),
                destinationTarget.get()));
    }
}
