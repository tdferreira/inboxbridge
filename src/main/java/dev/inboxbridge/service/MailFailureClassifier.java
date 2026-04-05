package dev.inboxbridge.service;

import java.time.Duration;
import java.util.Locale;

final class MailFailureClassifier {

    private static final Duration DEFAULT_FAILURE_BACKOFF = Duration.ofMinutes(2);
    private static final Duration TRANSIENT_FAILURE_BACKOFF = Duration.ofMinutes(5);
    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofMinutes(15);
    private static final Duration AUTH_FAILURE_BACKOFF = Duration.ofMinutes(30);

    private MailFailureClassifier() {
    }

    static Classification classify(String errorMessage) {
        String normalized = normalize(errorMessage);
        if (normalized.isBlank()) {
            return new Classification(FailureCategory.UNKNOWN, normalized);
        }
        if (containsAny(normalized, "429", "too many", "rate limit", "throttl", "quota", "temporarily blocked", "try again later", "lockout")) {
            return new Classification(FailureCategory.RATE_LIMIT, normalized);
        }
        if (containsAny(normalized,
                "invalid_grant",
                "consent_required",
                "invalid token",
                "token expired",
                "expired token",
                "access revoked",
                "session invalidated",
                "oauth token",
                "insufficient_scope",
                "interaction_required")) {
            return new Classification(FailureCategory.AUTHORIZATION, normalized);
        }
        if (containsAny(normalized,
                "authenticate failed",
                "authenticationfailed",
                "basicauthblocked",
                "logondenied",
                "login failed",
                "invalid credentials",
                "logon failure",
                "authentication unsuccessful")) {
            return new Classification(FailureCategory.AUTHENTICATION, normalized);
        }
        if (containsAny(normalized,
                "service unavailable",
                "temporarily unavailable",
                "server busy",
                "bad gateway",
                "gateway timeout",
                "internal server error",
                "mailbox unavailable")) {
            return new Classification(FailureCategory.PROVIDER_UNAVAILABLE, normalized);
        }
        if (containsAny(normalized,
                "timeout",
                "timed out",
                "connection refused",
                "connection reset",
                "socket closed",
                "broken pipe",
                "unexpected eof",
                "eofexception",
                "i/o",
                "ioexception")) {
            return new Classification(FailureCategory.TRANSIENT_NETWORK, normalized);
        }
        if (containsAny(normalized,
                "folderclosedexception",
                "folder closed",
                "folder not found",
                "no such folder",
                "mailbox locked",
                "mailbox is locked",
                "read-only mailbox")) {
            return new Classification(FailureCategory.MAILBOX_STATE, normalized);
        }
        return new Classification(FailureCategory.UNKNOWN, normalized);
    }

    static Classification classify(Throwable error) {
        return classify(normalize(error));
    }

    private static String normalize(String errorMessage) {
        return errorMessage == null ? "" : errorMessage.toLowerCase(Locale.ROOT);
    }

    private static String normalize(Throwable error) {
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(' ');
                }
                builder.append(message.toLowerCase(Locale.ROOT));
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private static boolean containsAny(String normalized, String... patterns) {
        for (String pattern : patterns) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    enum FailureCategory {
        RATE_LIMIT,
        AUTHENTICATION,
        AUTHORIZATION,
        PROVIDER_UNAVAILABLE,
        TRANSIENT_NETWORK,
        MAILBOX_STATE,
        UNKNOWN
    }

    record Classification(FailureCategory category, String normalizedMessage) {

        Duration baseBackoff() {
            return switch (category) {
                case RATE_LIMIT -> RATE_LIMIT_BACKOFF;
                case AUTHENTICATION, AUTHORIZATION -> AUTH_FAILURE_BACKOFF;
                case PROVIDER_UNAVAILABLE, TRANSIENT_NETWORK, MAILBOX_STATE -> TRANSIENT_FAILURE_BACKOFF;
                case UNKNOWN -> DEFAULT_FAILURE_BACKOFF;
            };
        }

        int throttleSeverityDelta() {
            return switch (category) {
                case RATE_LIMIT -> 2;
                case PROVIDER_UNAVAILABLE, TRANSIENT_NETWORK, MAILBOX_STATE -> 1;
                case AUTHENTICATION, AUTHORIZATION, UNKNOWN -> 0;
            };
        }

        boolean retryableOAuthSessionFailure() {
            return category == FailureCategory.AUTHENTICATION || category == FailureCategory.AUTHORIZATION;
        }
    }
}
