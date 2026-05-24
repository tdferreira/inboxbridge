package dev.inboxbridge.service.destination;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollSettings;
import dev.inboxbridge.domain.SourceSpamJunkStrategy;
import dev.inboxbridge.dto.GmailImportResponse;
import dev.inboxbridge.dto.MailImportResponse;

class GmailApiMailDestinationServiceTest {

    @Test
    void importMessageResolvesLabelsFromTheFetchedSourceFolder() {
        GmailApiMailDestinationService service = new GmailApiMailDestinationService();
        RecordingGmailLabelService labelService = new RecordingGmailLabelService();
        RecordingGmailImportService importService = new RecordingGmailImportService();
        service.gmailLabelService = labelService;
        service.gmailImportService = importService;

        GmailApiDestinationTarget destination = new GmailApiDestinationTarget(
                "user-gmail:4",
                4L,
                "alice",
                "GMAIL_API",
                "me",
                "client",
                "secret",
                "refresh",
                "https://localhost:3000/api/google-oauth/callback",
                true,
                false,
                false);
        RuntimeEmailAccount source = new RuntimeEmailAccount(
                "source-a",
                "USER",
                4L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "",
                Optional.of("INBOX, Projects/2026"),
                false,
                SourceFetchMode.POLLING,
                Optional.of("Imported/Default"),
                Optional.of("Projects/2026=Imported/Projects"),
                SourcePostPollSettings.none(),
                destination);
        FetchedMessage message = new FetchedMessage(
                "source-a",
                "source-key",
                Optional.of("<message@example.com>"),
                Instant.parse("2026-05-24T12:00:00Z"),
                Optional.of("Projects/2026"),
                9L,
                42L,
                null,
                "raw".getBytes());

        MailImportResponse response = service.importMessage(destination, source, message);

        assertEquals("gmail-message-1", response.destinationMessageId());
        assertEquals(Optional.of("Projects/2026"), labelService.sourceFolder);
        assertEquals(false, labelService.routeToSpam);
        assertEquals(Optional.of("Projects/2026=Imported/Projects"), labelService.folderLabelMappings);
        assertEquals(List.of("INBOX", "UNREAD", "Label_Projects"), importService.labelIds);
    }

    @Test
    void importMessageAsksGmailLabelsToRouteConfiguredSpamJunkFolderToSpam() {
        GmailApiMailDestinationService service = new GmailApiMailDestinationService();
        RecordingGmailLabelService labelService = new RecordingGmailLabelService();
        RecordingGmailImportService importService = new RecordingGmailImportService();
        service.gmailLabelService = labelService;
        service.gmailImportService = importService;

        GmailApiDestinationTarget destination = new GmailApiDestinationTarget(
                "user-gmail:4",
                4L,
                "alice",
                "GMAIL_API",
                "me",
                "client",
                "secret",
                "refresh",
                "https://localhost:3000/api/google-oauth/callback",
                true,
                false,
                false);
        RuntimeEmailAccount source = new RuntimeEmailAccount(
                "source-a",
                "USER",
                4L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                SourceFetchMode.POLLING,
                Optional.of("Imported/Default"),
                Optional.empty(),
                SourceSpamJunkStrategy.IMPORT_AND_ROUTE,
                Optional.of("Junk"),
                SourcePostPollSettings.none(),
                destination);
        FetchedMessage message = new FetchedMessage(
                "source-a",
                "source-key",
                Optional.of("<message@example.com>"),
                Instant.parse("2026-05-24T12:00:00Z"),
                Optional.of("Junk"),
                9L,
                42L,
                null,
                "raw".getBytes());

        service.importMessage(destination, source, message);

        assertEquals(true, labelService.routeToSpam);
        assertEquals(List.of("INBOX", "UNREAD", "Label_Projects"), importService.labelIds);
    }

    private static final class RecordingGmailLabelService extends GmailLabelService {
        private Optional<String> folderLabelMappings = Optional.empty();
        private Optional<String> sourceFolder = Optional.empty();
        private boolean routeToSpam;

        @Override
        public List<String> resolveLabelIds(
                GmailTarget target,
                Optional<String> customLabel,
                Optional<String> folderLabelMappings,
                Optional<String> sourceFolder,
                boolean routeToSpam) {
            this.folderLabelMappings = folderLabelMappings;
            this.sourceFolder = sourceFolder;
            this.routeToSpam = routeToSpam;
            return List.of("INBOX", "UNREAD", "Label_Projects");
        }
    }

    private static final class RecordingGmailImportService extends GmailImportService {
        private List<String> labelIds = List.of();

        @Override
        public GmailImportResponse importMessage(GmailTarget target, byte[] rawMessage, List<String> labelIds) {
            this.labelIds = labelIds;
            return new GmailImportResponse("gmail-message-1", "thread-1");
        }
    }
}
