package dev.inboxbridge.domain;

import java.time.Instant;
import java.util.Optional;

public record FetchedMessage(
        String sourceAccountId,
        String sourceMessageKey,
        Optional<String> messageIdHeader,
        Instant messageInstant,
        Optional<String> folderName,
        Long uidValidity,
        Long uid,
        String popUidl,
        byte[] rawMessage) {

    public FetchedMessage(
            String sourceAccountId,
            String sourceMessageKey,
            Optional<String> messageIdHeader,
            Instant messageInstant,
            byte[] rawMessage) {
        this(sourceAccountId, sourceMessageKey, messageIdHeader, messageInstant, Optional.empty(), null, null, null, rawMessage);
    }
}
