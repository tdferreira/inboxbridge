package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AuthLoginThrottleState;
import dev.inboxbridge.persistence.AuthLoginThrottleStateRepository;

class AuthLoginProtectionServiceTest {

    @Test
    void blocksAfterThresholdAndDoublesTheLockoutDuration() {
        AuthLoginProtectionService service = new AuthLoginProtectionService();
        service.repository = new InMemoryAuthLoginThrottleStateRepository();
        service.authSecuritySettingsService = authSecuritySettingsService(3, Duration.ofMinutes(5), Duration.ofMinutes(30));

        assertEquals(Duration.ofMinutes(5), service.calculateBlockDuration(1));
        assertEquals(Duration.ofMinutes(10), service.calculateBlockDuration(2));

        assertEquals(false, service.recordFailedLogin("203.0.113.10").blocked());
        assertEquals(false, service.recordFailedLogin("203.0.113.10").blocked());

        AuthLoginProtectionService.FailureResult firstBlock = service.recordFailedLogin("203.0.113.10");
        assertEquals(true, firstBlock.blocked());
        assertDoesNotThrow(() -> service.recordSuccessfulLogin("198.51.100.10"));

        service.recordSuccessfulLogin("203.0.113.10");
        service.recordFailedLogin("203.0.113.10");
        service.recordFailedLogin("203.0.113.10");
        AuthLoginProtectionService.FailureResult secondBlock = service.recordFailedLogin("203.0.113.10");
        assertEquals(true, secondBlock.blocked());
    }

    @Test
    void requireLoginAllowedRejectsActiveBlock() {
        AuthLoginProtectionService service = new AuthLoginProtectionService();
        service.repository = new InMemoryAuthLoginThrottleStateRepository();
        service.authSecuritySettingsService = authSecuritySettingsService(1, Duration.ofMinutes(5), Duration.ofMinutes(30));

        AuthLoginProtectionService.FailureResult blocked = service.recordFailedLogin("198.51.100.24");

        AuthLoginProtectionService.LoginBlockedException error = assertThrows(
                AuthLoginProtectionService.LoginBlockedException.class,
                () -> service.requireLoginAllowed("198.51.100.24"));

        assertEquals(blocked.blockedUntil(), error.blockedUntil());
    }

    private AuthSecuritySettingsService authSecuritySettingsService(int threshold, Duration initialBlock, Duration maxBlock) {
        return new AuthSecuritySettingsService() {
            @Override
            public EffectiveAuthSecuritySettings effectiveSettings() {
                return new EffectiveAuthSecuritySettings(
                        threshold,
                        initialBlock,
                        maxBlock,
                        true,
                        Duration.ofMinutes(10),
                        "ALTCHA",
                        "",
                        "",
                        "",
                        "",
                        false,
                        "IPWHOIS",
                        "IPAPI_CO,IP_API,IPINFO_LITE",
                        Duration.ofDays(30),
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(3),
                        "");
            }
        };
    }

    private static final class InMemoryAuthLoginThrottleStateRepository extends AuthLoginThrottleStateRepository {
        private final Map<String, AuthLoginThrottleState> states = new LinkedHashMap<>();
        private long nextId = 1L;

        @Override
        public Optional<AuthLoginThrottleState> findByClientKey(String clientKey) {
            return Optional.ofNullable(states.get(clientKey));
        }

        @Override
        public void persist(AuthLoginThrottleState entity) {
            if (entity.id == null) {
                entity.id = nextId++;
            }
            states.put(entity.clientKey, entity);
        }
    }
}
