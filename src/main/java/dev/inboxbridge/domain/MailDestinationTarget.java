package dev.inboxbridge.domain;

public sealed interface MailDestinationTarget permits GmailApiDestinationTarget, ImapAppendDestinationTarget {

    String subjectKey();

    Long userId();

    String ownerUsername();

    String providerId();

    String deliveryMode();
}