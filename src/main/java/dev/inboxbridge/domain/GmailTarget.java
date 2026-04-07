package dev.inboxbridge.domain;

import dev.inboxbridge.service.oauth.GoogleOAuthService;

public record GmailTarget(
        String subjectKey,
        Long userId,
        String ownerUsername,
        String destinationUser,
        String clientId,
        String clientSecret,
        String refreshToken,
        String redirectUri,
        boolean createMissingLabels,
        boolean neverMarkSpam,
        boolean processForCalendar) {

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
