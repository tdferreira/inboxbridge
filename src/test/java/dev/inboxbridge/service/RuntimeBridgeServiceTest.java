package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.UserBridge;

class RuntimeBridgeServiceTest {

    @Test
    void listEnabledForPollingSkipsInactiveUserOwners() {
        RuntimeBridgeService service = new RuntimeBridgeService();
        service.envSourceService = new EnvSourceService() {
            @Override
            public List<IndexedSource> configuredSources() {
                return List.of();
            }
        };
        service.userBridgeService = new UserBridgeService() {
            @Override
            public List<UserBridge> listEnabledBridges() {
                return List.of(userBridge(1L, "active-user-bridge"), userBridge(2L, "inactive-user-bridge"));
            }

            @Override
            public String decryptPassword(UserBridge bridge) {
                return "secret";
            }

            @Override
            public String decryptRefreshToken(UserBridge bridge) {
                return "";
            }
        };
        service.userGmailConfigService = new UserGmailConfigService() {
            @Override
            public Optional<ResolvedUserGmailConfig> resolveForUser(Long userId) {
                return Optional.of(new ResolvedUserGmailConfig(
                        userId,
                        "me",
                        "client-id",
                        "client-secret",
                        "refresh-token",
                        "https://localhost:3000/api/google-oauth/callback",
                        true,
                        false,
                        false));
            }
        };
        service.appUserRepository = new AppUserRepository() {
            @Override
            public Optional<AppUser> findByIdOptional(Long id) {
                return switch (Math.toIntExact(id)) {
                    case 1 -> Optional.of(user(1L, true, true, "alice"));
                    case 2 -> Optional.of(user(2L, false, true, "bob"));
                    default -> Optional.empty();
                };
            }
        };

        assertEquals(List.of("active-user-bridge"), service.listEnabledForPolling().stream().map(bridge -> bridge.id()).toList());
    }

    private static AppUser user(Long id, boolean active, boolean approved, String username) {
        AppUser user = new AppUser();
        user.id = id;
        user.active = active;
        user.approved = approved;
        user.username = username;
        user.role = AppUser.Role.USER;
        return user;
    }

    private static UserBridge userBridge(Long userId, String bridgeId) {
        UserBridge bridge = new UserBridge();
        bridge.userId = userId;
        bridge.bridgeId = bridgeId;
        bridge.enabled = true;
        bridge.protocol = BridgeConfig.Protocol.IMAP;
        bridge.host = "imap.example.com";
        bridge.port = 993;
        bridge.tls = true;
        bridge.authMethod = BridgeConfig.AuthMethod.PASSWORD;
        bridge.oauthProvider = BridgeConfig.OAuthProvider.NONE;
        bridge.username = bridgeId + "@example.com";
        bridge.folderName = "INBOX";
        bridge.unreadOnly = false;
        bridge.customLabel = null;
        bridge.createdAt = Instant.now();
        bridge.updatedAt = bridge.createdAt;
        return bridge;
    }
}