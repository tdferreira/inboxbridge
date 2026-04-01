package dev.inboxbridge.domain;

import java.util.Optional;

public record SourcePostPollSettings(
        boolean markAsRead,
        SourcePostPollAction action,
        Optional<String> targetFolder) {

    public static SourcePostPollSettings none() {
        return new SourcePostPollSettings(false, SourcePostPollAction.NONE, Optional.empty());
    }

    public boolean hasAnyAction() {
        return markAsRead || action != SourcePostPollAction.NONE;
    }
}
