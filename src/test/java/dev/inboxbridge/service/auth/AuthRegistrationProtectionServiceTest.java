package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AuthRegistrationThrottleState;
import dev.inboxbridge.persistence.AuthRegistrationThrottleStateRepository;

class AuthRegistrationProtectionServiceTest {

    @Test
    void blocksAfterThresholdAndDoublesTheLockoutDuration() {
        AuthRegistrationProtectionService service = new AuthRegistrationProtectionService();
        service.repository = new InMemoryAuthRegistrationThrottleStateRepository();
        service.authLoginProtectionService = authLoginProtectionService(3, Duration.ofMinutes(5), Duration.ofMinutes(30));
        service.authSecuritySettingsService = service.authLoginProtectionService.authSecuritySettingsService;

        assertEquals(false, service.recordFailedRegistration("203.0.113.10").blocked());
        assertEquals(false, service.recordFailedRegistration("203.0.113.10").blocked());

        AuthLoginProtectionService.FailureResult firstBlock = service.recordFailedRegistration("203.0.113.10");
        assertEquals(true, firstBlock.blocked());
        assertDoesNotThrow(() -> service.recordSuccessfulRegistration("198.51.100.10"));

        service.recordSuccessfulRegistration("203.0.113.10");
        service.recordFailedRegistration("203.0.113.10");
        service.recordFailedRegistration("203.0.113.10");
        AuthLoginProtectionService.FailureResult secondBlock = service.recordFailedRegistration("203.0.113.10");
        assertEquals(true, secondBlock.blocked());
    }

    @Test
    void requireRegistrationAllowedRejectsActiveBlock() {
        AuthRegistrationProtectionService service = new AuthRegistrationProtectionService();
        service.repository = new InMemoryAuthRegistrationThrottleStateRepository();
        service.authLoginProtectionService = authLoginProtectionService(1, Duration.ofMinutes(5), Duration.ofMinutes(30));
        service.authSecuritySettingsService = service.authLoginProtectionService.authSecuritySettingsService;

        AuthLoginProtectionService.FailureResult blocked = service.recordFailedRegistration("198.51.100.24");

        AuthRegistrationProtectionService.RegistrationBlockedException error = assertThrows(
                AuthRegistrationProtectionService.RegistrationBlockedException.class,
                () -> service.requireRegistrationAllowed("198.51.100.24"));

        assertEquals(blocked.blockedUntil(), error.blockedUntil());
    }

    private AuthLoginProtectionService authLoginProtectionService(int threshold, Duration initialBlock, Duration maxBlock) {
        AuthLoginProtectionService service = new AuthLoginProtectionService();
        service.authSecuritySettingsService = new AuthSecuritySettingsService() {
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
        return service;
    }

    private static final class InMemoryAuthRegistrationThrottleStateRepository extends AuthRegistrationThrottleStateRepository {
        private final Map<String, AuthRegistrationThrottleState> states = new LinkedHashMap<>();
        private long nextId = 1L;

        @Override
        public Optional<AuthRegistrationThrottleState> findByClientKey(String clientKey) {
            return Optional.ofNullable(states.get(clientKey));
        }

        @Override
        public void persist(AuthRegistrationThrottleState entity) {
            if (entity.id == null) {
                entity.id = nextId++;
            }
            states.put(entity.clientKey, entity);
        }
    }
}
