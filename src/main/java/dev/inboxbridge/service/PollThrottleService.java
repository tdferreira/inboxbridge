package dev.inboxbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jboss.logging.Logger;

import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.persistence.PollThrottleLease;
import dev.inboxbridge.persistence.PollThrottleLeaseRepository;
import dev.inboxbridge.persistence.PollThrottleState;
import dev.inboxbridge.persistence.PollThrottleStateRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Applies persisted throttling so provider pressure survives app restarts and
 * repeated polling across the same host/provider is paced across concurrent
 * workers on a single deployment database.
 */
@ApplicationScoped
public class PollThrottleService {

    private static final Logger LOG = Logger.getLogger(PollThrottleService.class);

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    PollThrottleStateRepository stateRepository;

    @Inject
    PollThrottleLeaseRepository leaseRepository;

    public ThrottleLease acquireSourceMailboxPermit(RuntimeEmailAccount emailAccount) {
        PollingSettingsService.EffectiveThrottleSettings settings = pollingSettingsService.effectiveThrottleSettings();
        return acquirePermit(sourceThrottleKey(emailAccount), "SOURCE_HOST", settings.sourceHostMinSpacing(),
                settings.sourceHostMaxConcurrency());
    }

    public ThrottleLease acquireDestinationDeliveryPermit(MailDestinationTarget target) {
        PollingSettingsService.EffectiveThrottleSettings settings = pollingSettingsService.effectiveThrottleSettings();
        return acquirePermit(destinationThrottleKey(target), destinationThrottleKind(target),
                settings.destinationProviderMinSpacing(), settings.destinationProviderMaxConcurrency());
    }

    public void release(ThrottleLease lease) {
        if (lease == null || lease.noop() || lease.leaseToken() == null) {
            return;
        }
        releaseByToken(lease.leaseToken());
    }

    public void recordSourceSuccess(RuntimeEmailAccount emailAccount) {
        updateAdaptiveState(sourceThrottleKey(emailAccount), "SOURCE_HOST", -1, Duration.ZERO);
    }

    public void recordSourceFailure(RuntimeEmailAccount emailAccount, String errorMessage) {
        applyOutcome(sourceThrottleKey(emailAccount), "SOURCE_HOST",
                pollingSettingsService.effectiveThrottleSettings().sourceHostMinSpacing(), errorMessage);
    }

    public void recordDestinationSuccess(MailDestinationTarget target) {
        updateAdaptiveState(destinationThrottleKey(target), destinationThrottleKind(target), -1, Duration.ZERO);
    }

    public void recordDestinationFailure(MailDestinationTarget target, String errorMessage) {
        applyOutcome(destinationThrottleKey(target), destinationThrottleKind(target),
                pollingSettingsService.effectiveThrottleSettings().destinationProviderMinSpacing(), errorMessage);
    }

    public void awaitSourceMailboxTurn(RuntimeEmailAccount emailAccount) {
        ThrottleLease lease = acquireSourceMailboxPermit(emailAccount);
        release(lease);
    }

    public void awaitDestinationDeliveryTurn(MailDestinationTarget target) {
        ThrottleLease lease = acquireDestinationDeliveryPermit(target);
        release(lease);
    }

    protected Instant now() {
        return Instant.now();
    }

    protected void pause(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Polling throttling sleep was interrupted", interrupted);
        }
    }

    private ThrottleLease acquirePermit(String key, String kind, Duration baseSpacing, int maxConcurrency) {
        if (key == null || baseSpacing == null || baseSpacing.isZero() || baseSpacing.isNegative() || maxConcurrency <= 0) {
            return ThrottleLease.noopLease();
        }
        while (true) {
            AcquireDecision decision = tryAcquire(key, kind, baseSpacing, maxConcurrency);
            if (decision.acquired()) {
                return decision.lease();
            }
            if (!decision.waitFor().isZero() && !decision.waitFor().isNegative()) {
                LOG.debugf("Throttling %s for %d ms (adaptive, persisted)", key, decision.waitFor().toMillis());
                pause(decision.waitFor());
            }
        }
    }

    private void applyOutcome(String key, String kind, Duration baseSpacing, String errorMessage) {
        if (key == null || baseSpacing == null || baseSpacing.isZero() || baseSpacing.isNegative()) {
            return;
        }
        String normalized = normalizeError(errorMessage);
        if (normalized.isBlank()) {
            return;
        }
        int delta = errorSeverityDelta(normalized);
        if (delta <= 0) {
            return;
        }
        Duration penalty = baseSpacing.multipliedBy(delta);
        updateAdaptiveState(key, kind, delta, penalty);
    }

    private String sourceThrottleKey(RuntimeEmailAccount emailAccount) {
        return normalizeHost(emailAccount == null ? null : emailAccount.host())
                .map(host -> "source-host:" + host)
                .orElse(null);
    }

    private String destinationThrottleKey(MailDestinationTarget target) {
        if (target instanceof GmailApiDestinationTarget gmailTarget) {
            return "destination-provider:" + gmailTarget.deliveryMode().toLowerCase(Locale.ROOT);
        }
        if (target instanceof ImapAppendDestinationTarget imapTarget) {
            return normalizeHost(imapTarget.host())
                    .map(host -> "destination-host:" + host)
                    .orElse(null);
        }
        return null;
    }

    private String destinationThrottleKind(MailDestinationTarget target) {
        if (target instanceof GmailApiDestinationTarget) {
            return "DESTINATION_PROVIDER";
        }
        return "DESTINATION_HOST";
    }

    private Optional<String> normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(host.trim().toLowerCase(Locale.ROOT));
    }

    private String normalizeError(String errorMessage) {
        return errorMessage == null ? "" : errorMessage.toLowerCase(Locale.ROOT);
    }

    private int errorSeverityDelta(String normalized) {
        if (containsAny(normalized, "429", "too many", "rate limit", "throttl", "quota", "temporarily blocked", "try again later")) {
            return 2;
        }
        if (containsAny(normalized, "timeout", "timed out", "connection refused", "connection reset", "service unavailable", "temporarily unavailable")) {
            return 1;
        }
        return 0;
    }

    private boolean containsAny(String normalized, String... patterns) {
        for (String pattern : patterns) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    protected AcquireDecision tryAcquire(String key, String kind, Duration baseSpacing, int maxConcurrency) {
        Instant currentTime = now();
        PollThrottleState state = stateForUpdate(key, kind, currentTime);
        leaseRepository.deleteExpired(key, currentTime);
        long activeLeases = leaseRepository.countActive(key, currentTime);
        Instant earliestExpiry = leaseRepository.earliestActiveExpiry(key, currentTime).orElse(null);
        int multiplier = Math.max(1, state.adaptiveMultiplier);
        Duration effectiveSpacing = multiply(baseSpacing, multiplier);

        Instant availableAt = currentTime;
        if (state.nextAllowedAt != null && state.nextAllowedAt.isAfter(availableAt)) {
            availableAt = state.nextAllowedAt;
        }
        if (activeLeases >= maxConcurrency && earliestExpiry != null && earliestExpiry.isAfter(availableAt)) {
            availableAt = earliestExpiry;
        }

        if (availableAt.isAfter(currentTime)) {
            state.adaptiveMultiplier = Math.min(
                    pollingSettingsService.effectiveThrottleSettings().adaptiveThrottleMaxMultiplier(),
                    multiplier + 1);
            state.updatedAt = currentTime;
            stateRepository.persist(state);
            return AcquireDecision.waiting(Duration.between(currentTime, availableAt));
        }

        PollThrottleLease lease = new PollThrottleLease();
        lease.throttleKey = key;
        lease.leaseToken = UUID.randomUUID().toString();
        lease.acquiredAt = currentTime;
        lease.expiresAt = currentTime.plus(pollingSettingsService.effectiveThrottleSettings().throttleLeaseTtl());
        leaseRepository.persist(lease);

        state.nextAllowedAt = currentTime.plus(effectiveSpacing);
        state.updatedAt = currentTime;
        stateRepository.persist(state);

        return AcquireDecision.acquired(new ThrottleLease(key, lease.leaseToken, false));
    }

    @Transactional
    protected void updateAdaptiveState(String key, String kind, int delta, Duration penalty) {
        if (key == null) {
            return;
        }
        Instant currentTime = now();
        PollThrottleState state = stateForUpdate(key, kind, currentTime);
        int maxMultiplier = Math.max(1, pollingSettingsService.effectiveThrottleSettings().adaptiveThrottleMaxMultiplier());
        int currentMultiplier = Math.max(1, state.adaptiveMultiplier);
        int nextMultiplier = currentMultiplier;
        if (delta < 0) {
            nextMultiplier = Math.max(1, currentMultiplier + delta);
        } else if (delta > 0) {
            nextMultiplier = Math.min(maxMultiplier, currentMultiplier + delta);
        }
        state.adaptiveMultiplier = nextMultiplier;
        if (penalty != null && !penalty.isZero() && !penalty.isNegative()) {
            Instant penalizedUntil = currentTime.plus(multiply(penalty, nextMultiplier));
            if (state.nextAllowedAt == null || penalizedUntil.isAfter(state.nextAllowedAt)) {
                state.nextAllowedAt = penalizedUntil;
            }
        }
        state.updatedAt = currentTime;
        stateRepository.persist(state);
    }

    @Transactional
    protected void releaseByToken(String leaseToken) {
        leaseRepository.deleteByLeaseToken(leaseToken);
    }

    private PollThrottleState stateForUpdate(String key, String kind, Instant currentTime) {
        Optional<PollThrottleState> existing = stateRepository.findByThrottleKeyForUpdate(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        PollThrottleState created = new PollThrottleState();
        created.throttleKey = key;
        created.throttleKind = kind;
        created.adaptiveMultiplier = 1;
        created.updatedAt = currentTime;
        stateRepository.persist(created);
        stateRepository.flush();
        return stateRepository.findByThrottleKeyForUpdate(key).orElse(created);
    }

    private Duration multiply(Duration duration, int multiplier) {
        if (duration == null || multiplier <= 1) {
            return duration == null ? Duration.ZERO : duration;
        }
        return duration.multipliedBy(multiplier);
    }

    public record ThrottleLease(String key, String leaseToken, boolean noop) {
        public static ThrottleLease noopLease() {
            return new ThrottleLease(null, null, true);
        }
    }

    protected record AcquireDecision(boolean acquired, Duration waitFor, ThrottleLease lease) {
        static AcquireDecision waiting(Duration waitFor) {
            return new AcquireDecision(false, waitFor, ThrottleLease.noopLease());
        }

        static AcquireDecision acquired(ThrottleLease lease) {
            return new AcquireDecision(true, Duration.ZERO, lease);
        }
    }
}
