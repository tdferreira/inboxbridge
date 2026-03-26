package dev.connexa.inboxbridge.service;

import java.time.Instant;

import dev.connexa.inboxbridge.domain.FetchedMessage;
import dev.connexa.inboxbridge.dto.GmailImportResponse;
import dev.connexa.inboxbridge.persistence.ImportedMessage;
import dev.connexa.inboxbridge.persistence.ImportedMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ImportDeduplicationService {

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    MimeHashService mimeHashService;

    public boolean alreadyImported(FetchedMessage message) {
        if (importedMessageRepository.existsBySourceMessageKey(message.sourceAccountId(), message.sourceMessageKey())) {
            return true;
        }
        String rawSha256 = mimeHashService.sha256Hex(message.rawMessage());
        return importedMessageRepository.existsByRawSha256(rawSha256);
    }

    @Transactional
    public void recordImport(FetchedMessage message, GmailImportResponse gmailResponse) {
        ImportedMessage entity = new ImportedMessage();
        entity.sourceAccountId = message.sourceAccountId();
        entity.sourceMessageKey = message.sourceMessageKey();
        entity.messageIdHeader = message.messageIdHeader().orElse(null);
        entity.rawSha256 = mimeHashService.sha256Hex(message.rawMessage());
        entity.gmailMessageId = gmailResponse.id();
        entity.gmailThreadId = gmailResponse.threadId();
        entity.importedAt = Instant.now();
        importedMessageRepository.persist(entity);
    }
}
