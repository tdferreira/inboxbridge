package dev.inboxbridge.service.destination;

import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.MailImportResponse;

public interface MailDestinationService {

    boolean supports(MailDestinationTarget target);

    boolean isLinked(MailDestinationTarget target);

    String notLinkedMessage(MailDestinationTarget target);

    MailImportResponse importMessage(MailDestinationTarget target, RuntimeEmailAccount bridge, FetchedMessage message);
}