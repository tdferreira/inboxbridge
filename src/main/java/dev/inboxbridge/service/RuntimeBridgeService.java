package dev.inboxbridge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.UserBridge;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RuntimeBridgeService {

    @Inject
    BridgeConfig config;

    @Inject
    UserBridgeService userBridgeService;

    @Inject
    UserGmailConfigService userGmailConfigService;

    @Inject
    AppUserRepository appUserRepository;

    @Inject
    EnvSourceService envSourceService;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    public Optional<RuntimeBridge> findSystemBridge(String sourceId) {
        return envSourceService.configuredSources().stream()
                .map(EnvSourceService.IndexedSource::source)
                .filter(source -> source.id().equals(sourceId))
                .findFirst()
                .map(source -> toRuntimeBridge(source, systemGmailTarget()));
    }

    public Optional<RuntimeBridge> findAccessibleForUser(AppUser actor, String sourceId) {
        // User-scoped endpoints must only target DB-managed fetchers. Env-managed
        // sources are exposed through the admin endpoints to avoid ID collisions.
        Optional<UserBridge> bridge = userBridgeService.findByBridgeId(sourceId);
        if (bridge.isEmpty() || !bridge.get().userId.equals(actor.id)) {
            return Optional.empty();
        }
        return toRuntimeBridge(bridge.get());
    }

    public List<RuntimeBridge> listEnabledForPolling() {
        List<RuntimeBridge> bridges = new ArrayList<>();
        GmailTarget systemTarget = systemGmailTarget();

        for (EnvSourceService.IndexedSource indexedSource : envSourceService.configuredSources()) {
            BridgeConfig.Source source = indexedSource.source();
            if (!source.enabled()) {
                continue;
            }
            bridges.add(toRuntimeBridge(source, systemTarget));
        }

        for (UserBridge bridge : userBridgeService.listEnabledBridges()) {
            toRuntimeBridge(bridge).ifPresent(bridges::add);
        }
        return bridges;
    }

    public List<RuntimeBridge> listEnabledForUser(AppUser actor) {
        List<RuntimeBridge> bridges = new ArrayList<>();
        for (UserBridge bridge : userBridgeService.listEnabledBridges()) {
            if (!bridge.userId.equals(actor.id)) {
                continue;
            }
            toRuntimeBridge(bridge).ifPresent(bridges::add);
        }
        return bridges;
    }

    public boolean gmailAccountLinked(RuntimeBridge bridge) {
        GmailTarget target = bridge.gmailTarget();
        if (target == null) {
            return false;
        }
        if (target.clientId() == null || target.clientId().isBlank()
                || target.clientSecret() == null || target.clientSecret().isBlank()) {
            return false;
        }
        if (target.refreshToken() != null && !target.refreshToken().isBlank()) {
            return true;
        }
        if (!oAuthCredentialService.secureStorageConfigured()) {
            return false;
        }
        return oAuthCredentialService.findGoogleCredential(target.subjectKey())
                .map(credential -> credential.refreshToken() != null && !credential.refreshToken().isBlank())
                .orElse(false);
    }

    private GmailTarget systemGmailTarget() {
        return new GmailTarget(
                "gmail-destination",
                null,
                "system",
                systemOAuthAppSettingsService.googleDestinationUser(),
                systemOAuthAppSettingsService.googleClientId(),
                systemOAuthAppSettingsService.googleClientSecret(),
                systemOAuthAppSettingsService.googleRefreshToken(),
                systemOAuthAppSettingsService.googleRedirectUri(),
                config.gmail().createMissingLabels(),
                config.gmail().neverMarkSpam(),
                config.gmail().processForCalendar());
    }

    private RuntimeBridge toRuntimeBridge(BridgeConfig.Source source, GmailTarget gmailTarget) {
        return new RuntimeBridge(
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
                gmailTarget);
    }

    private Optional<RuntimeBridge> toRuntimeBridge(UserBridge bridge) {
        Optional<AppUser> owner = appUserRepository.findByIdOptional(bridge.userId);
        Optional<UserGmailConfigService.ResolvedUserGmailConfig> gmailConfig = userGmailConfigService.resolveForUser(bridge.userId);
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        GmailTarget gmailTarget = gmailConfig
                .map(userTarget -> new GmailTarget(
                        "user-gmail:" + bridge.userId,
                        bridge.userId,
                        owner.get().username,
                        userTarget.destinationUser(),
                        userTarget.clientId(),
                        userTarget.clientSecret(),
                        userTarget.refreshToken(),
                        userTarget.redirectUri(),
                        userTarget.createMissingLabels(),
                        userTarget.neverMarkSpam(),
                        userTarget.processForCalendar()))
                .orElse(null);
        return Optional.of(new RuntimeBridge(
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
                userBridgeService.decryptPassword(bridge),
                userBridgeService.decryptRefreshToken(bridge),
                Optional.ofNullable(bridge.folderName),
                bridge.unreadOnly,
                Optional.ofNullable(bridge.customLabel),
                gmailTarget));
    }
}
