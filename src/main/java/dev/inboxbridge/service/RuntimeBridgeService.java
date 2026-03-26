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

    public List<RuntimeBridge> listEnabledForPolling() {
        List<RuntimeBridge> bridges = new ArrayList<>();
        GmailTarget systemTarget = systemGmailTarget();

        for (EnvSourceService.IndexedSource indexedSource : envSourceService.configuredSources()) {
            BridgeConfig.Source source = indexedSource.source();
            if (!source.enabled()) {
                continue;
            }
            bridges.add(new RuntimeBridge(
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
                    systemTarget));
        }

        for (UserBridge bridge : userBridgeService.listEnabledBridges()) {
            Optional<AppUser> owner = appUserRepository.findByIdOptional(bridge.userId);
            Optional<UserGmailConfigService.ResolvedUserGmailConfig> gmailConfig = userGmailConfigService.resolveForUser(bridge.userId);
            if (owner.isEmpty() || gmailConfig.isEmpty()) {
                continue;
            }
            UserGmailConfigService.ResolvedUserGmailConfig userTarget = gmailConfig.get();
            bridges.add(new RuntimeBridge(
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
                    new GmailTarget(
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
                            userTarget.processForCalendar())));
        }
        return bridges;
    }

    private GmailTarget systemGmailTarget() {
        return new GmailTarget(
                "gmail-destination",
                null,
                "system",
                config.gmail().destinationUser(),
                config.gmail().clientId(),
                config.gmail().clientSecret(),
                config.gmail().refreshToken(),
                config.gmail().redirectUri(),
                config.gmail().createMissingLabels(),
                config.gmail().neverMarkSpam(),
                config.gmail().processForCalendar());
    }
}
