package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.persistence.AppUser;
import jakarta.enterprise.inject.Vetoed;

class RemoteServiceTokenAuthServiceTest {

    @Test
    void authenticatesActiveApprovedUsersWithTheConfiguredBearerToken() {
        RemoteServiceTokenAuthService service = new RemoteServiceTokenAuthService();
        service.inboxBridgeConfig = new TestConfig(Optional.of("secret-token"), Optional.of("remote-admin"), Duration.ofHours(12));
        service.appUserService = new FakeAppUserService(activeApprovedUser("remote-admin"));

        Optional<AppUser> authenticated = service.authenticate("Bearer secret-token");

        assertTrue(authenticated.isPresent());
        assertEquals("remote-admin", authenticated.orElseThrow().username);
    }

    @Test
    void rejectsMissingMalformedOrWrongCredentials() {
        RemoteServiceTokenAuthService service = new RemoteServiceTokenAuthService();
        service.inboxBridgeConfig = new TestConfig(Optional.of("secret-token"), Optional.of("remote-admin"), Duration.ofHours(12));
        service.appUserService = new FakeAppUserService(activeApprovedUser("remote-admin"));

        assertTrue(service.authenticate(null).isEmpty());
        assertTrue(service.authenticate("Basic abc").isEmpty());
        assertTrue(service.authenticate("Bearer ").isEmpty());
        assertTrue(service.authenticate("Bearer wrong-token").isEmpty());
    }

    @Test
    void rejectsUsersThatAreMissingInactiveOrPendingApproval() {
        RemoteServiceTokenAuthService service = new RemoteServiceTokenAuthService();
        service.inboxBridgeConfig = new TestConfig(Optional.of("secret-token"), Optional.of("remote-admin"), Duration.ofHours(12));

        service.appUserService = new FakeAppUserService(null);
        assertTrue(service.authenticate("Bearer secret-token").isEmpty());

        AppUser inactive = activeApprovedUser("remote-admin");
        inactive.active = false;
        service.appUserService = new FakeAppUserService(inactive);
        assertTrue(service.authenticate("Bearer secret-token").isEmpty());

        AppUser pending = activeApprovedUser("remote-admin");
        pending.approved = false;
        service.appUserService = new FakeAppUserService(pending);
        assertTrue(service.authenticate("Bearer secret-token").isEmpty());
    }

    @Test
    void rejectsAuthenticationWhenTokenConfigurationIsIncomplete() {
        RemoteServiceTokenAuthService service = new RemoteServiceTokenAuthService();
        service.inboxBridgeConfig = new TestConfig(Optional.empty(), Optional.of("remote-admin"), Duration.ofHours(12));
        service.appUserService = new FakeAppUserService(activeApprovedUser("remote-admin"));
        assertTrue(service.authenticate("Bearer secret-token").isEmpty());

        service.inboxBridgeConfig = new TestConfig(Optional.of("secret-token"), Optional.empty(), Duration.ofHours(12));
        assertTrue(service.authenticate("Bearer secret-token").isEmpty());
    }

    private static AppUser activeApprovedUser(String username) {
        AppUser user = new AppUser();
        user.id = 1L;
        user.username = username;
        user.active = true;
        user.approved = true;
        user.role = AppUser.Role.ADMIN;
        return user;
    }

    @Vetoed
    private static final class FakeAppUserService extends AppUserService {
        private final AppUser user;

        private FakeAppUserService(AppUser user) {
            this.user = user;
        }

        @Override
        public Optional<AppUser> findByUsername(String username) {
            return Optional.ofNullable(user).filter(candidate -> candidate.username.equals(username));
        }
    }

    private static final class TestConfig implements InboxBridgeConfig {
        private final Optional<String> serviceToken;
        private final Optional<String> serviceUsername;
        private final Duration remoteSessionTtl;

        private TestConfig(Optional<String> serviceToken, Optional<String> serviceUsername, Duration remoteSessionTtl) {
            this.serviceToken = serviceToken;
            this.serviceUsername = serviceUsername;
            this.remoteSessionTtl = remoteSessionTtl;
        }

        @Override public boolean pollEnabled() { return true; }
        @Override public String pollInterval() { return "5m"; }
        @Override public int fetchWindow() { return 50; }
        @Override public Duration sourceHostMinSpacing() { return Duration.ofSeconds(1); }
        @Override public int sourceHostMaxConcurrency() { return 2; }
        @Override public Duration destinationProviderMinSpacing() { return Duration.ofMillis(250); }
        @Override public int destinationProviderMaxConcurrency() { return 1; }
        @Override public Duration throttleLeaseTtl() { return Duration.ofMinutes(2); }
        @Override public int adaptiveThrottleMaxMultiplier() { return 6; }
        @Override public double successJitterRatio() { return 0.2d; }
        @Override public Duration maxSuccessJitter() { return Duration.ofSeconds(30); }
        @Override public boolean multiUserEnabled() { return true; }
        @Override public Gmail gmail() { return null; }
        @Override public Microsoft microsoft() { return null; }
        @Override public List<Source> sources() { return List.of(); }

        @Override
        public Security security() {
            return new Security() {
                @Override public Auth auth() { return null; }
                @Override public Passkeys passkeys() { return null; }
                @Override
                public Remote remote() {
                    return new Remote() {
                        @Override public boolean enabled() { return true; }
                        @Override public Duration sessionTtl() { return remoteSessionTtl; }
                        @Override public int pollRateLimitCount() { return 60; }
                        @Override public Duration pollRateLimitWindow() { return Duration.ofMinutes(1); }
                        @Override public Optional<String> serviceToken() { return serviceToken; }
                        @Override public Optional<String> serviceUsername() { return serviceUsername; }
                    };
                }
            };
        }
    }
}
