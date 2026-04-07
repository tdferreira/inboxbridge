package dev.inboxbridge.domain;

import dev.inboxbridge.service.oauth.GoogleOAuthService;

public record GmailApiDestinationTarget(
        String subjectKey,
        Long userId,
        String ownerUsername,
        String providerId,
        String destinationUser,
        String clientId,
        String clientSecret,
        String refreshToken,
        String redirectUri,
        boolean createMissingLabels,
        boolean neverMarkSpam,
        boolean processForCalendar) implements MailDestinationTarget {

    @Override
    public String deliveryMode() {
        return "GMAIL_API";
    }

    public GoogleOAuthService.GoogleOAuthProfile oauthProfile() {
        return new GoogleOAuthService.GoogleOAuthProfile(
                subjectKey,
                clientId,
                clientSecret,
                refreshToken,
                redirectUri,
                GoogleOAuthService.GMAIL_TARGET_SCOPE);
    }
}