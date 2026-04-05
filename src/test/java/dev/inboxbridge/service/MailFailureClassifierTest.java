package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import jakarta.mail.MessagingException;

class MailFailureClassifierTest {

    @Test
    void classifiesRateLimitFailures() {
        MailFailureClassifier.Classification classification = MailFailureClassifier.classify("429 too many requests");

        assertEquals(MailFailureClassifier.FailureCategory.RATE_LIMIT, classification.category());
        assertEquals(Duration.ofMinutes(15), classification.baseBackoff());
        assertEquals(2, classification.throttleSeverityDelta());
        assertFalse(classification.retryableOAuthSessionFailure());
    }

    @Test
    void classifiesOauthAuthorizationFailures() {
        MailFailureClassifier.Classification classification = MailFailureClassifier.classify(
                new MessagingException("session invalidated", new MessagingException("invalid_grant")));

        assertEquals(MailFailureClassifier.FailureCategory.AUTHORIZATION, classification.category());
        assertEquals(Duration.ofMinutes(30), classification.baseBackoff());
        assertEquals(0, classification.throttleSeverityDelta());
        assertTrue(classification.retryableOAuthSessionFailure());
    }

    @Test
    void classifiesAuthenticationFailuresSeparatelyFromAuthorization() {
        MailFailureClassifier.Classification classification = MailFailureClassifier.classify("AUTHENTICATE failed for mailbox");

        assertEquals(MailFailureClassifier.FailureCategory.AUTHENTICATION, classification.category());
        assertEquals(Duration.ofMinutes(30), classification.baseBackoff());
        assertTrue(classification.retryableOAuthSessionFailure());
    }

    @Test
    void classifiesProviderAvailabilityFailures() {
        MailFailureClassifier.Classification classification = MailFailureClassifier.classify("503 service unavailable");

        assertEquals(MailFailureClassifier.FailureCategory.PROVIDER_UNAVAILABLE, classification.category());
        assertEquals(Duration.ofMinutes(5), classification.baseBackoff());
        assertEquals(1, classification.throttleSeverityDelta());
    }

    @Test
    void classifiesMailboxStateFailures() {
        MailFailureClassifier.Classification classification = MailFailureClassifier.classify("FolderClosedException while reading mailbox");

        assertEquals(MailFailureClassifier.FailureCategory.MAILBOX_STATE, classification.category());
        assertEquals(Duration.ofMinutes(5), classification.baseBackoff());
        assertEquals(1, classification.throttleSeverityDelta());
    }

    @Test
    void fallsBackToUnknownWhenNoKnownPatternMatches() {
        MailFailureClassifier.Classification classification = MailFailureClassifier.classify("unexpected parser mismatch");

        assertEquals(MailFailureClassifier.FailureCategory.UNKNOWN, classification.category());
        assertEquals(Duration.ofMinutes(2), classification.baseBackoff());
        assertEquals(0, classification.throttleSeverityDelta());
        assertFalse(classification.retryableOAuthSessionFailure());
    }
}
