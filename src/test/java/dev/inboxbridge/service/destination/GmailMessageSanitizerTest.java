package dev.inboxbridge.service.destination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

class GmailMessageSanitizerTest {

    private static final Session MAIL_SESSION = Session.getInstance(new Properties());

    @Test
    void removesDirectlyProhibitedAttachmentAndLeavesVisibleNotice() throws Exception {
        byte[] original = messageWithAttachment("malware.exe", "application/octet-stream", "dangerous");

        GmailMessageSanitizer.SanitizedMessage sanitized =
                new GmailMessageSanitizer().sanitize(original).orElseThrow();

        assertEquals(java.util.List.of("malware.exe"), sanitized.removedAttachmentNames());

        MimeMessage message = parse(sanitized.rawMessage());
        assertEquals("gmail-invalid-attachment", message.getHeader("X-InboxBridge-Sanitized", null));
        assertFalse(containsAttachmentNamed(message, "malware.exe"));
        assertTrue(textContent(message).contains("malware.exe"));
        assertTrue(textContent(message).contains("Gmail attachment security policy"));
    }

    @Test
    void replacesProhibitedSinglePartMessageBodyWithVisibleNotice() throws Exception {
        MimeMessage message = new MimeMessage(MAIL_SESSION);
        message.setFrom(new InternetAddress("sender@example.com"));
        message.setRecipients(Message.RecipientType.TO, "recipient@example.com");
        message.setSubject("Single attachment", StandardCharsets.UTF_8.name());
        message.setContent("dangerous".getBytes(StandardCharsets.UTF_8), "application/octet-stream");
        message.setHeader("Content-Disposition", "attachment; filename=\"malware.exe\"");
        message.saveChanges();
        byte[] original = write(message);

        GmailMessageSanitizer.SanitizedMessage sanitized =
                new GmailMessageSanitizer().sanitize(original).orElseThrow();

        MimeMessage sanitizedMessage = parse(sanitized.rawMessage());
        assertEquals(java.util.List.of("malware.exe"), sanitized.removedAttachmentNames());
        assertTrue(sanitizedMessage.isMimeType("text/plain"));
        assertTrue(textContent(sanitizedMessage).contains("malware.exe"));
        assertEquals(null, sanitizedMessage.getDisposition());
    }

    @Test
    void leavesDkimSignedMessageByteForByteUntouched() throws Exception {
        MimeMessage message = parse(messageWithAttachment("malware.exe", "application/octet-stream", "dangerous"));
        message.setHeader("DKIM-Signature", "v=1; a=rsa-sha256; d=example.com; s=test; bh=bodyhash; b=signature");
        byte[] original = write(message);
        byte[] originalSnapshot = original.clone();

        assertTrue(new GmailMessageSanitizer().sanitize(original).isEmpty());
        assertArrayEquals(originalSnapshot, original);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ARC-Seal", "ARC-Message-Signature" })
    void leavesArcProtectedMessageByteForByteUntouched(String arcHeader) throws Exception {
        MimeMessage message = parse(messageWithAttachment("malware.exe", "application/octet-stream", "dangerous"));
        message.setHeader(arcHeader, "i=1; a=rsa-sha256; d=example.com; s=test; b=signature");
        byte[] original = write(message);
        byte[] originalSnapshot = original.clone();

        assertTrue(new GmailMessageSanitizer().sanitize(original).isEmpty());
        assertArrayEquals(originalSnapshot, original);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "multipart/signed; protocol=\"application/pkcs7-signature\"",
            "application/pkcs7-mime; smime-type=signed-data",
            "application/x-pkcs7-mime; smime-type=enveloped-data",
            "multipart/signed; protocol=\"application/pgp-signature\"",
            "multipart/encrypted; protocol=\"application/pgp-encrypted\"",
            "application/pgp-encrypted"
    })
    void leavesSmimeAndPgpMimeProtectedMessagesByteForByteUntouched(String protectedContentType) throws Exception {
        byte[] original = messageWithProtectedEntityAndAttachment(protectedContentType, "malware.exe");
        byte[] originalSnapshot = original.clone();

        assertTrue(new GmailMessageSanitizer().sanitize(original).isEmpty());
        assertArrayEquals(originalSnapshot, original);
    }

    @Test
    void removesArchiveAttachmentContainingProhibitedFile() throws Exception {
        byte[] archive = zip(
                new ArchiveEntryContent("documents/readme.txt", "safe".getBytes(StandardCharsets.UTF_8)),
                new ArchiveEntryContent("tools/malware.exe", "dangerous".getBytes(StandardCharsets.UTF_8)));
        byte[] original = messageWithAttachment("documents.zip", "application/zip", archive);

        GmailMessageSanitizer.SanitizedMessage sanitized =
                new GmailMessageSanitizer().sanitize(original).orElseThrow();

        assertEquals(java.util.List.of("documents.zip"), sanitized.removedAttachmentNames());
        MimeMessage message = parse(sanitized.rawMessage());
        assertFalse(containsAttachmentNamed(message, "documents.zip"));
        assertTrue(textContent(message).contains("documents.zip"));
        assertTrue(textContent(message).contains("tools/malware.exe"));
    }

    @Test
    void removesArchiveAttachmentContainingProhibitedFileInNestedArchive() throws Exception {
        byte[] nestedArchive = zip(
                new ArchiveEntryContent("scripts/payload.js", "dangerous".getBytes(StandardCharsets.UTF_8)));
        byte[] outerArchive = zip(new ArchiveEntryContent("nested.zip", nestedArchive));
        byte[] original = messageWithAttachment("documents.zip", "application/zip", outerArchive);

        GmailMessageSanitizer.SanitizedMessage sanitized =
                new GmailMessageSanitizer().sanitize(original).orElseThrow();

        assertEquals(java.util.List.of("documents.zip"), sanitized.removedAttachmentNames());
        assertTrue(textContent(parse(sanitized.rawMessage())).contains("nested.zip!/scripts/payload.js"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "bundle.tar.gz", "bundle.tgz", "bundle.tar.bz2", "bundle.tbz2" })
    void removesCompressedTarAttachmentContainingProhibitedFile(String archiveFilename) throws Exception {
        boolean bzip2 = archiveFilename.endsWith(".bz2") || archiveFilename.endsWith(".tbz2");
        byte[] archive = compressedTar(
                bzip2,
                new ArchiveEntryContent("scripts/install.ps1", "dangerous".getBytes(StandardCharsets.UTF_8)));
        byte[] original = messageWithAttachment(archiveFilename, "application/octet-stream", archive);

        GmailMessageSanitizer.SanitizedMessage sanitized =
                new GmailMessageSanitizer().sanitize(original).orElseThrow();

        assertEquals(java.util.List.of(archiveFilename), sanitized.removedAttachmentNames());
        assertTrue(textContent(parse(sanitized.rawMessage())).contains("scripts/install.ps1"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "payload.exe.gz", "payload.exe.bz2" })
    void removesSingleStreamCompressionContainingProhibitedFile(String attachmentFilename) throws Exception {
        byte[] compressed = compressedStream(
                attachmentFilename.endsWith(".bz2"),
                "dangerous".getBytes(StandardCharsets.UTF_8));
        byte[] original = messageWithAttachment(
                attachmentFilename,
                "application/octet-stream",
                compressed);

        GmailMessageSanitizer.SanitizedMessage sanitized =
                new GmailMessageSanitizer().sanitize(original).orElseThrow();

        assertEquals(java.util.List.of(attachmentFilename), sanitized.removedAttachmentNames());
        assertTrue(textContent(parse(sanitized.rawMessage())).contains("payload.exe"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ade", "adp", "apk", "appx", "appxbundle", "bat", "cab", "chm", "cmd", "com", "cpl",
            "diagcab", "diagcfg", "diagpkg", "dll", "dmg", "ex", "ex_", "exe", "hta", "img", "ins",
            "iso", "isp", "jar", "jnlp", "js", "jse", "lib", "lnk", "mde", "mjs", "msc", "msi",
            "msix", "msixbundle", "msp", "mst", "nsh", "pif", "ps1", "scr", "sct", "shb", "sys",
            "vb", "vbe", "vbs", "vhd", "vxd", "wsc", "wsf", "wsh", "xll"
    })
    void removesEveryPublishedGmailProhibitedFileExtensionCaseInsensitively(String extension) throws Exception {
        String filename = "blocked." + extension.toUpperCase(java.util.Locale.ROOT);
        byte[] original = messageWithAttachment(filename, "application/octet-stream", "dangerous");

        GmailMessageSanitizer.SanitizedMessage sanitized =
                new GmailMessageSanitizer().sanitize(original).orElseThrow();

        assertEquals(java.util.List.of(filename), sanitized.removedAttachmentNames());
        assertFalse(containsAttachmentNamed(parse(sanitized.rawMessage()), filename));
    }

    @Test
    void leavesMessageWithOnlyAllowedAttachmentsUntouched() throws Exception {
        byte[] original = messageWithAttachment("document.pdf", "application/pdf", "safe");
        byte[] originalSnapshot = original.clone();

        assertTrue(new GmailMessageSanitizer().sanitize(original).isEmpty());
        assertArrayEquals(originalSnapshot, original);
    }

    @Test
    void leavesSafeArchiveAttachmentUntouched() throws Exception {
        byte[] archive = zip(
                new ArchiveEntryContent("documents/readme.txt", "safe".getBytes(StandardCharsets.UTF_8)),
                new ArchiveEntryContent("documents/report.pdf", "safe".getBytes(StandardCharsets.UTF_8)));
        byte[] original = messageWithAttachment("documents.zip", "application/zip", archive);
        byte[] originalSnapshot = original.clone();

        assertTrue(new GmailMessageSanitizer().sanitize(original).isEmpty());
        assertArrayEquals(originalSnapshot, original);
    }

    @Test
    void leavesMessageUntouchedWhenNestedArchiveExceedsInspectionDepth() throws Exception {
        byte[] archive = zip(
                new ArchiveEntryContent("payload.exe", "dangerous".getBytes(StandardCharsets.UTF_8)));
        for (int depth = 0; depth < 6; depth++) {
            archive = zip(new ArchiveEntryContent("level-" + depth + ".zip", archive));
        }
        byte[] original = messageWithAttachment("outer.zip", "application/zip", archive);
        byte[] originalSnapshot = original.clone();

        assertTrue(new GmailMessageSanitizer().sanitize(original).isEmpty());
        assertArrayEquals(originalSnapshot, original);
    }

    @Test
    void leavesMessageUntouchedWhenArchiveExceedsInspectionEntryLimit() throws Exception {
        ByteArrayOutputStream archiveOutput = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(archiveOutput)) {
            for (int index = 0; index < 1_001; index++) {
                archive.putNextEntry(new ZipEntry("safe-" + index + ".txt"));
                archive.closeEntry();
            }
            archive.putNextEntry(new ZipEntry("payload.exe"));
            archive.closeEntry();
        }
        byte[] original = messageWithAttachment(
                "too-many-entries.zip",
                "application/zip",
                archiveOutput.toByteArray());
        byte[] originalSnapshot = original.clone();

        assertTrue(new GmailMessageSanitizer().sanitize(original).isEmpty());
        assertArrayEquals(originalSnapshot, original);
    }

    @Test
    void leavesMessageUntouchedWhenArchiveIsMalformed() throws Exception {
        byte[] original = messageWithAttachment(
                "malformed.zip",
                "application/zip",
                "not a zip archive".getBytes(StandardCharsets.UTF_8));
        byte[] originalSnapshot = original.clone();

        assertTrue(new GmailMessageSanitizer().sanitize(original).isEmpty());
        assertArrayEquals(originalSnapshot, original);
    }

    @Test
    void leavesOuterMessageUntouchedWhenAttachedMessageHasDkimSignature() throws Exception {
        MimeMessage nestedMessage = parse(messageWithAttachment(
                "nested-document.pdf",
                "application/pdf",
                "safe"));
        nestedMessage.setHeader(
                "DKIM-Signature",
                "v=1; a=rsa-sha256; d=example.com; s=test; bh=bodyhash; b=signature");

        MimeMessage outerMessage = new MimeMessage(MAIL_SESSION);
        outerMessage.setFrom(new InternetAddress("sender@example.com"));
        outerMessage.setRecipients(Message.RecipientType.TO, "recipient@example.com");
        outerMessage.setSubject("Forwarded signed message", StandardCharsets.UTF_8.name());
        MimeBodyPart attachedMessage = new MimeBodyPart();
        attachedMessage.setContent(nestedMessage, "message/rfc822");
        MimeBodyPart prohibitedAttachment = new MimeBodyPart();
        prohibitedAttachment.setContent("dangerous", "application/octet-stream");
        prohibitedAttachment.setFileName("malware.exe");
        MimeMultipart multipart = new MimeMultipart("mixed");
        multipart.addBodyPart(attachedMessage);
        multipart.addBodyPart(prohibitedAttachment);
        outerMessage.setContent(multipart);
        outerMessage.saveChanges();
        byte[] original = write(outerMessage);
        byte[] originalSnapshot = original.clone();

        assertTrue(new GmailMessageSanitizer().sanitize(original).isEmpty());
        assertArrayEquals(originalSnapshot, original);
    }

    @Test
    void removesProhibitedAttachmentInsideUnsignedAttachedMessage() throws Exception {
        MimeMessage nestedMessage = parse(messageWithAttachment(
                "nested-malware.exe",
                "application/octet-stream",
                "dangerous"));
        MimeMessage outerMessage = new MimeMessage(MAIL_SESSION);
        outerMessage.setFrom(new InternetAddress("sender@example.com"));
        outerMessage.setRecipients(Message.RecipientType.TO, "recipient@example.com");
        outerMessage.setSubject("Forwarded unsigned message", StandardCharsets.UTF_8.name());
        MimeBodyPart attachedMessage = new MimeBodyPart();
        attachedMessage.setContent(nestedMessage, "message/rfc822");
        MimeMultipart multipart = new MimeMultipart("mixed");
        multipart.addBodyPart(attachedMessage);
        outerMessage.setContent(multipart);
        outerMessage.saveChanges();

        GmailMessageSanitizer.SanitizedMessage sanitized =
                new GmailMessageSanitizer().sanitize(write(outerMessage)).orElseThrow();

        assertEquals(java.util.List.of("nested-malware.exe"), sanitized.removedAttachmentNames());
        assertFalse(containsAttachmentNamed(parse(sanitized.rawMessage()), "nested-malware.exe"));
        assertTrue(textContent(parse(sanitized.rawMessage())).contains("nested-malware.exe"));
    }

    private byte[] messageWithAttachment(String filename, String contentType, String content) throws Exception {
        return messageWithAttachment(filename, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] messageWithAttachment(String filename, String contentType, byte[] content) throws Exception {
        MimeMessage message = new MimeMessage(MAIL_SESSION);
        message.setFrom(new InternetAddress("sender@example.com"));
        message.setRecipients(Message.RecipientType.TO, "recipient@example.com");
        message.setSubject("Attachment test", StandardCharsets.UTF_8.name());

        MimeBodyPart text = new MimeBodyPart();
        text.setText("Original body", StandardCharsets.UTF_8.name());

        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setContent(content, contentType);
        attachment.setFileName(filename);

        MimeMultipart multipart = new MimeMultipart("mixed");
        multipart.addBodyPart(text);
        multipart.addBodyPart(attachment);
        message.setContent(multipart);
        message.saveChanges();
        return write(message);
    }

    private byte[] zip(ArchiveEntryContent... entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (ArchiveEntryContent entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                zip.write(entry.content());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private byte[] compressedTar(boolean bzip2, ArchiveEntryContent... entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (java.io.OutputStream compressor = bzip2
                ? new BZip2CompressorOutputStream(output)
                : new GzipCompressorOutputStream(output);
                TarArchiveOutputStream tar = new TarArchiveOutputStream(compressor)) {
            for (ArchiveEntryContent entry : entries) {
                TarArchiveEntry tarEntry = new TarArchiveEntry(entry.name());
                tarEntry.setSize(entry.content().length);
                tar.putArchiveEntry(tarEntry);
                tar.write(entry.content());
                tar.closeArchiveEntry();
            }
            tar.finish();
        }
        return output.toByteArray();
    }

    private byte[] compressedStream(boolean bzip2, byte[] content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (java.io.OutputStream compressor = bzip2
                ? new BZip2CompressorOutputStream(output)
                : new GzipCompressorOutputStream(output)) {
            compressor.write(content);
        }
        return output.toByteArray();
    }

    private byte[] messageWithProtectedEntityAndAttachment(String protectedContentType, String filename) throws Exception {
        MimeMessage message = new MimeMessage(MAIL_SESSION);
        message.setFrom(new InternetAddress("sender@example.com"));
        message.setRecipients(Message.RecipientType.TO, "recipient@example.com");
        message.setSubject("Protected message", StandardCharsets.UTF_8.name());

        MimeBodyPart protectedPart = new MimeBodyPart();
        String multipartParameters = null;
        if (protectedContentType.startsWith("multipart/")) {
            int parameterSeparator = protectedContentType.indexOf(';');
            String subtype = protectedContentType.substring(
                    "multipart/".length(),
                    parameterSeparator);
            multipartParameters = protectedContentType.substring(parameterSeparator);
            MimeMultipart protectedMultipart = new MimeMultipart(subtype);
            MimeBodyPart protectedPayload = new MimeBodyPart();
            protectedPayload.setText("cryptographically protected content", StandardCharsets.UTF_8.name());
            MimeBodyPart signatureOrControl = new MimeBodyPart();
            signatureOrControl.setText("signature or encryption control", StandardCharsets.UTF_8.name());
            protectedMultipart.addBodyPart(protectedPayload);
            protectedMultipart.addBodyPart(signatureOrControl);
            protectedPart.setContent(protectedMultipart);
        } else {
            protectedPart.setContent("cryptographically protected content", protectedContentType);
        }

        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setContent("dangerous".getBytes(StandardCharsets.UTF_8), "application/octet-stream");
        attachment.setFileName(filename);

        MimeMultipart multipart = new MimeMultipart("mixed");
        multipart.addBodyPart(protectedPart);
        multipart.addBodyPart(attachment);
        message.setContent(multipart);
        message.saveChanges();
        if (multipartParameters != null) {
            protectedPart.setHeader("Content-Type", protectedPart.getContentType() + multipartParameters);
        }
        return write(message);
    }

    private MimeMessage parse(byte[] rawMessage) throws Exception {
        return new MimeMessage(MAIL_SESSION, new ByteArrayInputStream(rawMessage));
    }

    private byte[] write(MimeMessage message) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        message.writeTo(output);
        return output.toByteArray();
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

    private String textContent(jakarta.mail.Part part) throws Exception {
        if (part.isMimeType("text/*")) {
            return part.getContent().toString();
        }
        if (part.isMimeType("message/rfc822") && part.getContent() instanceof jakarta.mail.Part nestedPart) {
            return textContent(nestedPart);
        }
        if (!part.isMimeType("multipart/*")) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        Multipart multipart = (Multipart) part.getContent();
        for (int index = 0; index < multipart.getCount(); index++) {
            text.append(textContent(multipart.getBodyPart(index)));
        }
        return text.toString();
    }

    private record ArchiveEntryContent(String name, byte[] content) {
    }
}
