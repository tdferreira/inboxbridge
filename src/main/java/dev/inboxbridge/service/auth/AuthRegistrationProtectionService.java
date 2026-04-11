package dev.inboxbridge.service.auth;

import java.time.Instant;

import dev.inboxbridge.persistence.AuthRegistrationThrottleState;
import dev.inboxbridge.persistence.AuthRegistrationThrottleStateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Applies the same client-address backoff policy used for sign-in attempts to
 * self-registration so repeated CAPTCHA failures or username-guessing bursts do
 * not hammer the registration endpoint indefinitely.
 */
@ApplicationScoped
public class AuthRegistrationProtectionService {

    @Inject
    AuthRegistrationThrottleStateRepository repository;

    @Inject
    AuthLoginProtectionService authLoginProtectionService;

    @Inject
    AuthSecuritySettingsService authSecuritySettingsService;

    public void requireRegistrationAllowed(String clientKey) {
        AuthRegistrationThrottleState state = repository.findByClientKey(normalize(clientKey)).orElse(null);
        if (state == null || state.blockedUntil == null || !state.blockedUntil.isAfter(Instant.now())) {
            return;
        }
        throw new RegistrationBlockedException(state.blockedUntil);
    }

    @Transactional
    public AuthLoginProtectionService.FailureResult recordFailedRegistration(String clientKey) {
        Instant now = Instant.now();
        AuthRegistrationThrottleState state = repository.findByClientKey(normalize(clientKey))
                .orElseGet(AuthRegistrationThrottleState::new);
        if (state.id == null) {
            state.clientKey = normalize(clientKey);
            state.failureCount = 0;
            state.lockoutCount = 0;
        }
        if (state.blockedUntil != null && state.blockedUntil.isAfter(now)) {
            return new AuthLoginProtectionService.FailureResult(true, state.blockedUntil);
        }

        state.failureCount += 1;
        state.updatedAt = now;

        int threshold = authSecuritySettingsService.effectiveSettings().loginFailureThreshold();
        if (state.failureCount < threshold) {
            repository.persist(state);
            return new AuthLoginProtectionService.FailureResult(false, null);
        }

        state.failureCount = 0;
        state.lockoutCount += 1;
        state.blockedUntil = now.plus(authLoginProtectionService.calculateBlockDuration(state.lockoutCount));
        state.updatedAt = now;
        repository.persist(state);
        return new AuthLoginProtectionService.FailureResult(true, state.blockedUntil);
    }

    @Transactional
    public void recordSuccessfulRegistration(String clientKey) {
        repository.findByClientKey(normalize(clientKey)).ifPresent(state -> {
            state.failureCount = 0;
            state.lockoutCount = 0;
            state.blockedUntil = null;
            state.updatedAt = Instant.now();
        });
    }

    private String normalize(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return "unknown";
        }
        return clientKey.trim();
    }

    public static final class RegistrationBlockedException extends IllegalArgumentException {
        private final Instant blockedUntil;

        public RegistrationBlockedException(Instant blockedUntil) {
            super("Too many failed registration attempts from this address.");
            this.blockedUntil = blockedUntil;
        }

        public Instant blockedUntil() {
            return blockedUntil;
        }
    }
}
