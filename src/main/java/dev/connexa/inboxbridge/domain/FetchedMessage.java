package dev.connexa.inboxbridge.domain;

import java.time.Instant;
import java.util.Optional;

public record FetchedMessage(
        String sourceAccountId,
        String sourceMessageKey,
        Optional<String> messageIdHeader,
        Instant messageInstant,
        byte[] rawMessage) {
}
