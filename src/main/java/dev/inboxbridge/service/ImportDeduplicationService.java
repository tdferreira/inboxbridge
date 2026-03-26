package dev.inboxbridge.service;

import java.time.Instant;

import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.dto.GmailImportResponse;
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

    public boolean alreadyImported(FetchedMessage message, GmailTarget target) {
        if (importedMessageRepository.existsBySourceMessageKey(target.subjectKey(), message.sourceAccountId(), message.sourceMessageKey())) {
            return true;
        }
        String rawSha256 = mimeHashService.sha256Hex(message.rawMessage());
        return importedMessageRepository.existsByRawSha256(target.subjectKey(), rawSha256);
    }

    @Transactional
    public void recordImport(FetchedMessage message, GmailTarget target, GmailImportResponse gmailResponse) {
        ImportedMessage entity = new ImportedMessage();
        entity.sourceAccountId = message.sourceAccountId();
        entity.sourceMessageKey = message.sourceMessageKey();
        entity.messageIdHeader = message.messageIdHeader().orElse(null);
        entity.rawSha256 = mimeHashService.sha256Hex(message.rawMessage());
        entity.destinationKey = target.subjectKey();
        entity.gmailMessageId = gmailResponse.id();
        entity.gmailThreadId = gmailResponse.threadId();
        entity.importedAt = Instant.now();
        importedMessageRepository.persist(entity);
    }
}
