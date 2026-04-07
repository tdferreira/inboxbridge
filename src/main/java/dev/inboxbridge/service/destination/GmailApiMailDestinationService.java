package dev.inboxbridge.service.destination;

import java.util.List;

import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.GmailImportResponse;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.service.oauth.OAuthCredentialService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GmailApiMailDestinationService implements MailDestinationService {

    public static final String GMAIL_ACCOUNT_NOT_LINKED_MESSAGE =
            "The Gmail destination is not linked for this account. Connect it from My Destination Mailbox before polling this source.";

    @Inject
    GmailImportService gmailImportService;

    @Inject
    GmailLabelService gmailLabelService;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Override
    public boolean supports(MailDestinationTarget target) {
        return target instanceof GmailApiDestinationTarget;
    }

    @Override
    public boolean isLinked(MailDestinationTarget target) {
        if (!(target instanceof GmailApiDestinationTarget gmailTarget)) {
            return false;
        }
        if (gmailTarget.clientId() == null || gmailTarget.clientId().isBlank()
                || gmailTarget.clientSecret() == null || gmailTarget.clientSecret().isBlank()) {
            return false;
        }
        if (gmailTarget.refreshToken() != null && !gmailTarget.refreshToken().isBlank()) {
            return true;
        }
        if (!oAuthCredentialService.secureStorageConfigured()) {
            return false;
        }
        return oAuthCredentialService.findGoogleCredential(gmailTarget.subjectKey())
                .map(credential -> credential.refreshToken() != null && !credential.refreshToken().isBlank())
                .orElse(false);
    }

    @Override
    public String notLinkedMessage(MailDestinationTarget target) {
        return GMAIL_ACCOUNT_NOT_LINKED_MESSAGE;
    }

    @Override
    public MailImportResponse importMessage(MailDestinationTarget target, RuntimeEmailAccount bridge, FetchedMessage message) {
        GmailApiDestinationTarget gmailTarget = (GmailApiDestinationTarget) target;
        GmailTarget compatibilityTarget = asCompatibilityTarget(gmailTarget);
        List<String> labelIds = gmailLabelService.resolveLabelIds(compatibilityTarget, bridge.customLabel());
        GmailImportResponse response = gmailImportService.importMessage(compatibilityTarget, message.rawMessage(), labelIds);
        return new MailImportResponse(response.id(), response.threadId());
    }

    private GmailTarget asCompatibilityTarget(GmailApiDestinationTarget target) {
        return new GmailTarget(
                target.subjectKey(),
                target.userId(),
                target.ownerUsername(),
                target.destinationUser(),
                target.clientId(),
                target.clientSecret(),
                target.refreshToken(),
                target.redirectUri(),
                target.createMissingLabels(),
                target.neverMarkSpam(),
                target.processForCalendar());
    }
}
