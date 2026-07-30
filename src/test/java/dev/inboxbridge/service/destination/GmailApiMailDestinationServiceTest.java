package dev.inboxbridge.service.destination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

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
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

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

    @Test
    void retriesRejectedUnsignedMessageWithoutDirectlyProhibitedAttachment() throws Exception {
        GmailApiMailDestinationService service = new GmailApiMailDestinationService();
        RejectOnceGmailImportService importService = new RejectOnceGmailImportService();
        service.gmailLabelService = new RecordingGmailLabelService();
        service.gmailImportService = importService;
        service.gmailMessageSanitizer = new GmailMessageSanitizer();

        GmailApiDestinationTarget destination = destination();
        RuntimeEmailAccount source = source(destination);
        FetchedMessage message = fetchedMessage(messageWithAttachment("malware.exe", "dangerous"));

        MailImportResponse response = service.importMessage(destination, source, message);

        assertEquals(List.of("malware.exe"), response.removedAttachmentNames());
        assertEquals(2, importService.rawMessages.size());
        assertEquals(
                java.util.HexFormat.of().formatHex(message.rawMessage()),
                java.util.HexFormat.of().formatHex(importService.rawMessages.getFirst()));
        assertFalse(containsAttachmentNamed(importService.rawMessages.get(1), "malware.exe"));
    }

    @Test
    void retriesProductionShapedGenericRejectionWithSanitizedMessage() throws Exception {
        GenericRejectOnceGmailImportService importService = new GenericRejectOnceGmailImportService();
        GmailApiMailDestinationService service = new GmailApiMailDestinationService(
                importService,
                new RecordingGmailLabelService(),
                new GmailMessageSanitizer());
        FetchedMessage message = fetchedMessage(messageWithAttachment("malware.exe", "dangerous"));

        MailImportResponse response = service.importMessage(destination(), source(destination()), message);

        assertEquals(List.of("malware.exe"), response.removedAttachmentNames());
        assertEquals(2, importService.rawMessages.size());
        assertEquals(
                java.util.HexFormat.of().formatHex(message.rawMessage()),
                java.util.HexFormat.of().formatHex(importService.rawMessages.getFirst()));
        assertFalse(containsAttachmentNamed(importService.rawMessages.get(1), "malware.exe"));
    }

    @Test
    void doesNotRetryUnrelatedGenericGmailBadRequest() throws Exception {
        UnrelatedBadRequestGmailImportService importService = new UnrelatedBadRequestGmailImportService();
        GmailApiMailDestinationService service = new GmailApiMailDestinationService(
                importService,
                new RecordingGmailLabelService(),
                new GmailMessageSanitizer());
        FetchedMessage message = fetchedMessage(messageWithAttachment("malware.exe", "dangerous"));

        assertThrows(
                IllegalStateException.class,
                () -> service.importMessage(destination(), source(destination()), message));

        assertEquals(1, importService.rawMessages.size());
    }

    @Test
    void doesNotRetryOrRewriteDkimSignedMessage() throws Exception {
        GmailApiMailDestinationService service = new GmailApiMailDestinationService();
        GenericAlwaysRejectingGmailImportService importService = new GenericAlwaysRejectingGmailImportService();
        service.gmailLabelService = new RecordingGmailLabelService();
        service.gmailImportService = importService;
        service.gmailMessageSanitizer = new GmailMessageSanitizer();

        byte[] rawMessage = messageWithAttachment("malware.exe", "dangerous");
        MimeMessage signedMessage = new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(rawMessage));
        signedMessage.setHeader(
                "DKIM-Signature",
                "v=1; a=rsa-sha256; d=example.com; s=test; bh=bodyhash; b=signature");
        ByteArrayOutputStream signedOutput = new ByteArrayOutputStream();
        signedMessage.writeTo(signedOutput);
        FetchedMessage message = fetchedMessage(signedOutput.toByteArray());

        assertThrows(
                IllegalStateException.class,
                () -> service.importMessage(destination(), source(destination()), message));

        assertEquals(1, importService.rawMessages.size());
        assertEquals(
                java.util.HexFormat.of().formatHex(message.rawMessage()),
                java.util.HexFormat.of().formatHex(importService.rawMessages.getFirst()));
    }

    @Test
    void stopsAfterOneSanitizedRetryWhenGmailStillRejectsMessage() throws Exception {
        GmailApiMailDestinationService service = new GmailApiMailDestinationService();
        AlwaysRejectingGmailImportService importService = new AlwaysRejectingGmailImportService();
        service.gmailLabelService = new RecordingGmailLabelService();
        service.gmailImportService = importService;
        service.gmailMessageSanitizer = new GmailMessageSanitizer();
        FetchedMessage message = fetchedMessage(messageWithAttachment("malware.exe", "dangerous"));

        assertThrows(
                GmailInvalidAttachmentException.class,
                () -> service.importMessage(destination(), source(destination()), message));

        assertEquals(2, importService.rawMessages.size());
        assertFalse(containsAttachmentNamed(importService.rawMessages.get(1), "malware.exe"));
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

    private static final class RejectOnceGmailImportService extends GmailImportService {
        private final List<byte[]> rawMessages = new ArrayList<>();

        @Override
        public GmailImportResponse importMessage(GmailTarget target, byte[] rawMessage, List<String> labelIds) {
            rawMessages.add(rawMessage.clone());
            if (rawMessages.size() == 1) {
                throw new GmailInvalidAttachmentException(
                        400,
                        "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"Invalid attachment. Please check https://support.google.com/mail/answer/6590.\"}}");
            }
            return new GmailImportResponse("gmail-message-1", "thread-1");
        }
    }

    private static final class AlwaysRejectingGmailImportService extends GmailImportService {
        private final List<byte[]> rawMessages = new ArrayList<>();

        @Override
        public GmailImportResponse importMessage(GmailTarget target, byte[] rawMessage, List<String> labelIds) {
            rawMessages.add(rawMessage.clone());
            throw new GmailInvalidAttachmentException(
                    400,
                    "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"Invalid attachment. Please check https://support.google.com/mail/answer/6590.\"}}");
        }
    }

    private static final class GenericRejectOnceGmailImportService extends GmailImportService {
        private final List<byte[]> rawMessages = new ArrayList<>();

        @Override
        public GmailImportResponse importMessage(GmailTarget target, byte[] rawMessage, List<String> labelIds) {
            rawMessages.add(rawMessage.clone());
            if (rawMessages.size() == 1) {
                throw genericInvalidAttachmentRejection();
            }
            return new GmailImportResponse("gmail-message-1", "thread-1");
        }
    }

    private static final class GenericAlwaysRejectingGmailImportService extends GmailImportService {
        private final List<byte[]> rawMessages = new ArrayList<>();

        @Override
        public GmailImportResponse importMessage(GmailTarget target, byte[] rawMessage, List<String> labelIds) {
            rawMessages.add(rawMessage.clone());
            throw genericInvalidAttachmentRejection();
        }
    }

    private static final class UnrelatedBadRequestGmailImportService extends GmailImportService {
        private final List<byte[]> rawMessages = new ArrayList<>();

        @Override
        public GmailImportResponse importMessage(GmailTarget target, byte[] rawMessage, List<String> labelIds) {
            rawMessages.add(rawMessage.clone());
            throw new IllegalStateException("""
                    Failed to import Gmail message: 400 - {
                      "error": {
                        "message": "Invalid label",
                        "status": "INVALID_ARGUMENT"
                      }
                    }""");
        }
    }

    private static IllegalStateException genericInvalidAttachmentRejection() {
        return new IllegalStateException("""
                Failed to import Gmail message: 400 - {
                  "error": {
                    "code": 400,
                    "message": "Invalid attachment. Please check https://support.google.com/mail/answer/6590.",
                    "status": "INVALID_ARGUMENT"
                  }
                }""");
    }

    private GmailApiDestinationTarget destination() {
        return new GmailApiDestinationTarget(
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
    }

    private RuntimeEmailAccount source(GmailApiDestinationTarget destination) {
        return new RuntimeEmailAccount(
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
                SourcePostPollSettings.none(),
                destination);
    }

    private FetchedMessage fetchedMessage(byte[] rawMessage) {
        return new FetchedMessage(
                "source-a",
                "source-key",
                Optional.of("<message@example.com>"),
                Instant.parse("2026-07-30T12:00:00Z"),
                Optional.of("INBOX"),
                9L,
                42L,
                null,
                rawMessage);
    }

    private byte[] messageWithAttachment(String filename, String content) throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress("sender@example.com"));
        message.setRecipients(Message.RecipientType.TO, "recipient@example.com");
        message.setSubject("Attachment test", StandardCharsets.UTF_8.name());
        MimeBodyPart text = new MimeBodyPart();
        text.setText("Original body", StandardCharsets.UTF_8.name());
        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setContent(content.getBytes(StandardCharsets.UTF_8), "application/octet-stream");
        attachment.setFileName(filename);
        MimeMultipart multipart = new MimeMultipart("mixed");
        multipart.addBodyPart(text);
        multipart.addBodyPart(attachment);
        message.setContent(multipart);
        message.saveChanges();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        return output.toByteArray();
    }

    private boolean containsAttachmentNamed(byte[] rawMessage, String filename) throws Exception {
        MimeMessage message = new MimeMessage(
                Session.getInstance(new Properties()),
                new ByteArrayInputStream(rawMessage));
        return containsAttachmentNamed(message, filename);
    }

    private boolean containsAttachmentNamed(jakarta.mail.Part part, String filename) throws Exception {
        if (filename.equals(part.getFileName())) {
            return true;
        }
        if (!part.isMimeType("multipart/*")) {
            return false;
        }
        Multipart multipart = (Multipart) part.getContent();
        for (int index = 0; index < multipart.getCount(); index++) {
            BodyPart bodyPart = multipart.getBodyPart(index);
            if (containsAttachmentNamed(bodyPart, filename)) {
                return true;
            }
        }
        return false;
    }
}
