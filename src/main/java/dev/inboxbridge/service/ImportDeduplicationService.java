package dev.inboxbridge.service;

import java.time.Instant;

import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.persistence.ImportedMessage;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ImportDeduplicationService {

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    MimeHashService mimeHashService;

    public ImportDeduplicationService() {
    }

    public ImportDeduplicationService(
            ImportedMessageRepository importedMessageRepository,
            MimeHashService mimeHashService) {
        this.importedMessageRepository = importedMessageRepository;
        this.mimeHashService = mimeHashService;
    }

    @Transactional
    public boolean alreadyImported(FetchedMessage message, MailDestinationTarget target) {
        String destinationIdentityKey = DestinationIdentityKeys.forTarget(target);
        if (importedMessageRepository.existsBySourceMessageKey(destinationIdentityKey, message.sourceAccountId(), message.sourceMessageKey())) {
            return true;
        }
        String rawSha256 = mimeHashService.sha256Hex(message.rawMessage());
        if (importedMessageRepository.existsByRawSha256(destinationIdentityKey, rawSha256)) {
            return true;
        }
        String normalizedMessageIdHeader = normalizeMessageIdHeader(message.messageIdHeader().orElse(null));
        return normalizedMessageIdHeader != null
                && importedMessageRepository.existsByMessageIdHeader(destinationIdentityKey, message.sourceAccountId(), normalizedMessageIdHeader);
    }

    @Transactional
    public void recordImport(FetchedMessage message, MailDestinationTarget target, MailImportResponse importResponse) {
        ImportedMessage entity = new ImportedMessage();
        entity.sourceAccountId = message.sourceAccountId();
        entity.sourceMessageKey = message.sourceMessageKey();
        entity.messageIdHeader = normalizeMessageIdHeader(message.messageIdHeader().orElse(null));
        entity.rawSha256 = mimeHashService.sha256Hex(message.rawMessage());
        entity.destinationKey = target.subjectKey();
        entity.destinationIdentityKey = DestinationIdentityKeys.forTarget(target);
        entity.gmailMessageId = importResponse.destinationMessageId();
        entity.gmailThreadId = importResponse.destinationThreadId();
        entity.importedAt = Instant.now();
        importedMessageRepository.persist(entity);
    }

    private String normalizeMessageIdHeader(String messageIdHeader) {
        if (messageIdHeader == null) {
            return null;
        }
        String trimmed = messageIdHeader.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("<") && trimmed.endsWith(">") && trimmed.length() > 2) {
            String unwrapped = trimmed.substring(1, trimmed.length() - 1).trim();
            return unwrapped.isEmpty() ? null : unwrapped;
        }
        return trimmed;
    }
}
