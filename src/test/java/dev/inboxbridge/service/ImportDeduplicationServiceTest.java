package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.persistence.ImportedMessage;
import dev.inboxbridge.persistence.ImportedMessageRepository;

class ImportDeduplicationServiceTest {

    @Test
    void sameUserDifferentMailboxIdentityDoesNotShareDedupeState() {
        ImportDeduplicationService service = service();
        FetchedMessage message = message("source-1", "source-1:uid:10", "duplicate body");
        ImapAppendDestinationTarget firstTarget = destination("user-destination:7", "first@example.com");
        ImapAppendDestinationTarget secondTarget = destination("user-destination:7", "second@example.com");

        service.recordImport(message, firstTarget, new MailImportResponse("dest-1", "thread-1"));

        assertTrue(service.alreadyImported(message, firstTarget));
        assertFalse(service.alreadyImported(message, secondTarget));
    }

    @Test
    void differentSourcesStillShareRawMimeDedupeWithinOneDestinationIdentity() {
        ImportDeduplicationService service = service();
        ImapAppendDestinationTarget target = destination("user-destination:7", "first@example.com");
        byte[] identicalRaw = rawMessage("shared-message-id@example.com", "identical body");
        FetchedMessage first = message("source-1", "source-1:uid:10", "shared-message-id@example.com", identicalRaw);
        FetchedMessage second = message("source-2", "source-2:uid:20", "shared-message-id@example.com", identicalRaw);

        service.recordImport(first, target, new MailImportResponse("dest-1", "thread-1"));

        assertTrue(service.alreadyImported(second, target));
    }

    private ImportDeduplicationService service() {
        ImportDeduplicationService service = new ImportDeduplicationService();
        service.importedMessageRepository = new InMemoryImportedMessageRepository();
        service.mimeHashService = new MimeHashService();
        return service;
    }

    private FetchedMessage message(String sourceAccountId, String sourceMessageKey, String body) {
        byte[] raw = rawMessage(sourceMessageKey + "@example.com", body);
        return message(sourceAccountId, sourceMessageKey, sourceMessageKey + "@example.com", raw);
    }

    private FetchedMessage message(String sourceAccountId, String sourceMessageKey, String messageId, byte[] raw) {
        return new FetchedMessage(
                sourceAccountId,
                sourceMessageKey,
                Optional.of("<" + messageId + ">"),
                Instant.parse("2026-04-06T00:00:00Z"),
                Optional.of("INBOX"),
                44L,
                10L,
                "uidl-10",
                raw);
    }

    private byte[] rawMessage(String messageId, String body) {
        return ("Subject: test\r\nMessage-ID: <" + messageId + ">\r\n\r\n" + body)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private ImapAppendDestinationTarget destination(String subjectKey, String username) {
        return new ImapAppendDestinationTarget(
                subjectKey,
                7L,
                "alice",
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                username,
                "secret",
                "INBOX");
    }

    private static final class InMemoryImportedMessageRepository extends ImportedMessageRepository {
        private final List<ImportedMessage> importedMessages = new ArrayList<>();

        @Override
        public boolean existsBySourceMessageKey(String destinationIdentityKey, String sourceAccountId, String sourceMessageKey) {
            return importedMessages.stream().anyMatch(message ->
                    destinationIdentityKey.equals(message.destinationIdentityKey)
                            && sourceAccountId.equals(message.sourceAccountId)
                            && sourceMessageKey.equals(message.sourceMessageKey));
        }

        @Override
        public boolean existsByRawSha256(String destinationIdentityKey, String rawSha256) {
            return importedMessages.stream().anyMatch(message ->
                    destinationIdentityKey.equals(message.destinationIdentityKey)
                            && rawSha256.equals(message.rawSha256));
        }

        @Override
        public void persist(ImportedMessage entity) {
            importedMessages.add(entity);
        }
    }
}
