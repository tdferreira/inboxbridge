package dev.inboxbridge.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Applies lightweight in-process pacing so one InboxBridge instance does not
 * hit the same source host or destination provider in a tight burst.
 */
@ApplicationScoped
public class PollThrottleService {

    private static final Logger LOG = Logger.getLogger(PollThrottleService.class);

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    private final Map<String, Instant> nextAllowedAtByKey = new ConcurrentHashMap<>();

    public void awaitSourceMailboxTurn(RuntimeEmailAccount emailAccount) {
        String host = normalizeHost(emailAccount.host());
        if (host == null) {
            return;
        }
        awaitTurn("source-host:" + host, inboxBridgeConfig.sourceHostMinSpacing());
    }

    public void awaitDestinationDeliveryTurn(MailDestinationTarget target) {
        if (target instanceof GmailApiDestinationTarget gmailTarget) {
            awaitTurn("destination-provider:" + gmailTarget.deliveryMode().toLowerCase(Locale.ROOT),
                    inboxBridgeConfig.destinationProviderMinSpacing());
            return;
        }
        if (target instanceof ImapAppendDestinationTarget imapTarget) {
            String host = normalizeHost(imapTarget.host());
            if (host != null) {
                awaitTurn("destination-host:" + host, inboxBridgeConfig.destinationProviderMinSpacing());
            }
        }
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

    private void awaitTurn(String key, Duration spacing) {
        if (key == null || spacing == null || spacing.isZero() || spacing.isNegative()) {
            return;
        }
        Duration waitFor = Duration.ZERO;
        synchronized (nextAllowedAtByKey) {
            Instant currentTime = now();
            Instant nextAllowedAt = nextAllowedAtByKey.get(key);
            if (nextAllowedAt != null && nextAllowedAt.isAfter(currentTime)) {
                waitFor = Duration.between(currentTime, nextAllowedAt);
                nextAllowedAtByKey.put(key, nextAllowedAt.plus(spacing));
            } else {
                nextAllowedAtByKey.put(key, currentTime.plus(spacing));
            }
        }
        if (!waitFor.isZero() && !waitFor.isNegative()) {
            LOG.debugf("Throttling %s for %d ms to avoid provider hammering", key, waitFor.toMillis());
            pause(waitFor);
        }
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        return host.trim().toLowerCase(Locale.ROOT);
    }
}
