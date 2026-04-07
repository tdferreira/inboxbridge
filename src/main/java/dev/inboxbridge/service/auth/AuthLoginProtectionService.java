package dev.inboxbridge.service.auth;

import java.time.Duration;
import java.time.Instant;

import dev.inboxbridge.persistence.AuthLoginThrottleState;
import dev.inboxbridge.persistence.AuthLoginThrottleStateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuthLoginProtectionService {

    @Inject
    AuthLoginThrottleStateRepository repository;

    @Inject
    AuthSecuritySettingsService authSecuritySettingsService;

    public void requireLoginAllowed(String clientKey) {
        AuthLoginThrottleState state = repository.findByClientKey(normalize(clientKey)).orElse(null);
        if (state == null || state.blockedUntil == null || !state.blockedUntil.isAfter(Instant.now())) {
            return;
        }
        throw new LoginBlockedException(state.blockedUntil);
    }

    @Transactional
    public FailureResult recordFailedLogin(String clientKey) {
        Instant now = Instant.now();
        AuthLoginThrottleState state = repository.findByClientKey(normalize(clientKey)).orElseGet(AuthLoginThrottleState::new);
        if (state.id == null) {
            state.clientKey = normalize(clientKey);
            state.failureCount = 0;
            state.lockoutCount = 0;
        }
        if (state.blockedUntil != null && state.blockedUntil.isAfter(now)) {
            return new FailureResult(true, state.blockedUntil);
        }

        state.failureCount += 1;
        state.updatedAt = now;

        int threshold = authSecuritySettingsService.effectiveSettings().loginFailureThreshold();
        if (state.failureCount < threshold) {
            repository.persist(state);
            return new FailureResult(false, null);
        }

        state.failureCount = 0;
        state.lockoutCount += 1;
        Duration blockDuration = calculateBlockDuration(state.lockoutCount);
        state.blockedUntil = now.plus(blockDuration);
        state.updatedAt = now;
        repository.persist(state);
        return new FailureResult(true, state.blockedUntil);
    }

    @Transactional
    public void recordSuccessfulLogin(String clientKey) {
        repository.findByClientKey(normalize(clientKey)).ifPresent(state -> {
            state.failureCount = 0;
            state.lockoutCount = 0;
            state.blockedUntil = null;
            state.updatedAt = Instant.now();
        });
    }

    Duration calculateBlockDuration(int lockoutCount) {
        AuthSecuritySettingsService.EffectiveAuthSecuritySettings effective = authSecuritySettingsService.effectiveSettings();
        Duration duration = effective.loginInitialBlock();
        for (int i = 1; i < lockoutCount; i += 1) {
            duration = duration.multipliedBy(2);
            if (duration.compareTo(effective.loginMaxBlock()) >= 0) {
                return effective.loginMaxBlock();
            }
        }
        return duration.compareTo(effective.loginMaxBlock()) > 0
                ? effective.loginMaxBlock()
                : duration;
    }

    private String normalize(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return "unknown";
        }
        return clientKey.trim();
    }

    public record FailureResult(boolean blocked, Instant blockedUntil) {
    }

    public static final class LoginBlockedException extends IllegalArgumentException {
        private final Instant blockedUntil;

        public LoginBlockedException(Instant blockedUntil) {
            super("Too many failed sign-in attempts from this address.");
            this.blockedUntil = blockedUntil;
        }

        public Instant blockedUntil() {
            return blockedUntil;
        }
    }
}
