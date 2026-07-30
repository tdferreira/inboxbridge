package dev.inboxbridge.service.destination;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;

/**
 * Builds an explicitly marked Gmail-compatible derivative only when doing so
 * cannot invalidate DKIM, ARC, S/MIME, or PGP/MIME protection.
 *
 * <p>The caller keeps ownership of the original raw bytes. Any parse error,
 * unsupported protection, malformed archive, or inspection-limit breach
 * returns an empty result instead of partially rewriting the message.</p>
 */
@ApplicationScoped
public class GmailMessageSanitizer {

    private static final String SANITIZED_HEADER = "X-InboxBridge-Sanitized";
    private static final String SANITIZED_HEADER_VALUE = "gmail-invalid-attachment";
    private static final int MAX_ARCHIVE_DEPTH = 4;
    private static final int MAX_ARCHIVE_ENTRIES = 1_000;
    private static final long MAX_ARCHIVE_EXPANDED_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_NESTED_ARCHIVE_BYTES = 16 * 1024 * 1024;
    private static final Set<String> PROHIBITED_EXTENSIONS = Set.of(
            "ade", "adp", "apk", "appx", "appxbundle", "bat", "cab", "chm", "cmd", "com", "cpl",
            "diagcab", "diagcfg", "diagpkg", "dll", "dmg", "ex", "ex_", "exe", "hta", "img", "ins",
            "iso", "isp", "jar", "jnlp", "js", "jse", "lib", "lnk", "mde", "mjs", "msc", "msi",
            "msix", "msixbundle", "msp", "mst", "nsh", "pif", "ps1", "scr", "sct", "shb", "sys",
            "vb", "vbe", "vbs", "vhd", "vxd", "wsc", "wsf", "wsh", "xll");

    /**
     * Removes Gmail-prohibited attachments and returns a new raw MIME message,
     * or an empty result when no safe, useful derivative can be produced.
     */
    public Optional<SanitizedMessage> sanitize(byte[] rawMessage) {
        if (rawMessage == null || rawMessage.length == 0) {
            return Optional.empty();
        }
        try {
            MimeMessage message = new MimeMessage(
                    Session.getInstance(new Properties()),
                    new ByteArrayInputStream(rawMessage));
            if (hasCryptographicProtection(message)) {
                return Optional.empty();
            }
            List<String> removedAttachmentNames = new ArrayList<>();
            String rootFilename = message.getFileName();
            Optional<String> rootArchiveEntry = prohibitedArchiveEntry(message);
            if (isProhibitedFilename(rootFilename) || rootArchiveEntry.isPresent()) {
                removedAttachmentNames.add(rootFilename);
                message.setText(
                        removalNoticeText(rootFilename, rootArchiveEntry.orElse(null)),
                        StandardCharsets.UTF_8.name());
                message.removeHeader("Content-Disposition");
            } else {
                removeProhibitedAttachments(message, removedAttachmentNames);
            }
            if (removedAttachmentNames.isEmpty()) {
                return Optional.empty();
            }
            message.setHeader(SANITIZED_HEADER, SANITIZED_HEADER_VALUE);
            message.saveChanges();
            ByteArrayOutputStream output = new ByteArrayOutputStream(rawMessage.length);
            message.writeTo(output);
            return Optional.of(new SanitizedMessage(output.toByteArray(), removedAttachmentNames));
        } catch (MessagingException | IOException | RuntimeException ignored) {
            // A failed sanitization must leave the original raw message untouched.
            return Optional.empty();
        }
    }

    private boolean hasCryptographicProtection(Part part) throws MessagingException, IOException {
        if (part instanceof MimeMessage message
                && (message.getHeader("DKIM-Signature") != null
                        || message.getHeader("ARC-Seal") != null
                        || message.getHeader("ARC-Message-Signature") != null)) {
            return true;
        }
        if (part.isMimeType("multipart/signed")
                || part.isMimeType("multipart/encrypted")
                || part.isMimeType("application/pkcs7-mime")
                || part.isMimeType("application/x-pkcs7-mime")
                || part.isMimeType("application/pkcs7-signature")
                || part.isMimeType("application/x-pkcs7-signature")
                || part.isMimeType("application/pgp-encrypted")
                || part.isMimeType("application/pgp-signature")) {
            return true;
        }
        if (isSmimeFilename(part.getFileName())) {
            return true;
        }
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            return content instanceof Part nestedPart && hasCryptographicProtection(nestedPart);
        }
        if (!part.isMimeType("multipart/*")) {
            return false;
        }
        Multipart multipart = (Multipart) part.getContent();
        for (int index = 0; index < multipart.getCount(); index++) {
            if (hasCryptographicProtection(multipart.getBodyPart(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean isSmimeFilename(String filename) {
        if (filename == null) {
            return false;
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".p7m")
                || normalized.endsWith(".p7s")
                || normalized.endsWith(".p7c")
                || normalized.endsWith(".p7z");
    }

    private void removeProhibitedAttachments(Part part, List<String> removedAttachmentNames)
            throws MessagingException, IOException {
        if (part.isMimeType("message/rfc822")) {
            Object content = part.getContent();
            if (content instanceof Part nestedPart) {
                removeProhibitedAttachments(nestedPart, removedAttachmentNames);
                if (nestedPart instanceof MimeMessage nestedMessage) {
                    nestedMessage.saveChanges();
                }
            }
            return;
        }
        if (!part.isMimeType("multipart/*")) {
            return;
        }
        Multipart multipart = (Multipart) part.getContent();
        for (int index = multipart.getCount() - 1; index >= 0; index--) {
            BodyPart bodyPart = multipart.getBodyPart(index);
            String filename = bodyPart.getFileName();
            Optional<String> prohibitedArchiveEntry = prohibitedArchiveEntry(bodyPart);
            if (isProhibitedFilename(filename) || prohibitedArchiveEntry.isPresent()) {
                removedAttachmentNames.add(filename);
                multipart.removeBodyPart(index);
                multipart.addBodyPart(removalNotice(filename, prohibitedArchiveEntry.orElse(null)), index);
                continue;
            }
            removeProhibitedAttachments(bodyPart, removedAttachmentNames);
        }
    }

    private Optional<String> prohibitedArchiveEntry(Part part) throws MessagingException, IOException {
        String filename = part.getFileName();
        if (!isInspectableArchive(filename)) {
            return Optional.empty();
        }
        return prohibitedArchiveEntry(filename, part.getInputStream(), 0, new ArchiveScanBudget());
    }

    private Optional<String> prohibitedArchiveEntry(
            String filename,
            InputStream input,
            int depth,
            ArchiveScanBudget budget) throws IOException {
        if (depth > MAX_ARCHIVE_DEPTH) {
            throw new ArchiveScanLimitException();
        }
        String normalizedFilename = filename.toLowerCase(Locale.ROOT);
        if (normalizedFilename.endsWith(".zip")) {
            return prohibitedZipEntry(input, depth, budget);
        }
        if (normalizedFilename.endsWith(".tar.gz") || normalizedFilename.endsWith(".tgz")) {
            try (GzipCompressorInputStream gzip = new GzipCompressorInputStream(input)) {
                return prohibitedTarEntry(gzip, depth, budget);
            }
        }
        if (normalizedFilename.endsWith(".tar.bz2")
                || normalizedFilename.endsWith(".tbz")
                || normalizedFilename.endsWith(".tbz2")) {
            try (BZip2CompressorInputStream bzip2 = new BZip2CompressorInputStream(input)) {
                return prohibitedTarEntry(bzip2, depth, budget);
            }
        }
        if (normalizedFilename.endsWith(".tar")) {
            return prohibitedTarEntry(input, depth, budget);
        }
        if (normalizedFilename.endsWith(".gz")) {
            String derivedFilename = filename.substring(0, filename.length() - ".gz".length());
            try (GzipCompressorInputStream gzip = new GzipCompressorInputStream(input)) {
                String embeddedFilename = gzip.getMetaData().getFileName();
                return prohibitedCompressedEntry(
                        embeddedFilename == null || embeddedFilename.isBlank()
                                ? derivedFilename
                                : embeddedFilename,
                        gzip,
                        depth,
                        budget);
            }
        }
        if (normalizedFilename.endsWith(".bz2")) {
            String derivedFilename = filename.substring(0, filename.length() - ".bz2".length());
            try (BZip2CompressorInputStream bzip2 = new BZip2CompressorInputStream(input)) {
                return prohibitedCompressedEntry(derivedFilename, bzip2, depth, budget);
            }
        }
        return Optional.empty();
    }

    private Optional<String> prohibitedCompressedEntry(
            String expandedFilename,
            InputStream expandedContent,
            int depth,
            ArchiveScanBudget budget) throws IOException {
        if (isProhibitedFilename(expandedFilename)) {
            return Optional.of(expandedFilename);
        }
        if (!isInspectableArchive(expandedFilename)) {
            return Optional.empty();
        }
        if (depth == MAX_ARCHIVE_DEPTH) {
            throw new ArchiveScanLimitException();
        }
        byte[] nestedArchive = readNestedArchive(expandedContent, budget);
        Optional<String> nestedFinding = prohibitedArchiveEntry(
                expandedFilename,
                new ByteArrayInputStream(nestedArchive),
                depth + 1,
                budget);
        return nestedFinding.map(finding -> expandedFilename + "!/" + finding);
    }

    private Optional<String> prohibitedZipEntry(
            InputStream input,
            int depth,
            ArchiveScanBudget budget) throws IOException {
        try (ZipInputStream archive = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                budget.recordEntry();
                String entryName = entry.getName();
                if (isProhibitedFilename(entryName)) {
                    return Optional.of(entryName);
                }
                if (isInspectableArchive(entryName)) {
                    if (depth == MAX_ARCHIVE_DEPTH) {
                        throw new ArchiveScanLimitException();
                    }
                    byte[] nestedArchive = readNestedArchive(archive, budget);
                    Optional<String> nestedFinding = prohibitedArchiveEntry(
                            entryName,
                            new ByteArrayInputStream(nestedArchive),
                            depth + 1,
                            budget);
                    if (nestedFinding.isPresent()) {
                        return Optional.of(entryName + "!/" + nestedFinding.get());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> prohibitedTarEntry(
            InputStream input,
            int depth,
            ArchiveScanBudget budget) throws IOException {
        try (TarArchiveInputStream archive =
                new TarArchiveInputStream(new ArchiveBudgetInputStream(input, budget))) {
            TarArchiveEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                budget.recordEntry();
                String entryName = entry.getName();
                if (isProhibitedFilename(entryName)) {
                    return Optional.of(entryName);
                }
                if (isInspectableArchive(entryName)) {
                    if (depth == MAX_ARCHIVE_DEPTH) {
                        throw new ArchiveScanLimitException();
                    }
                    byte[] nestedArchive = readNestedArchive(archive, budget);
                    Optional<String> nestedFinding = prohibitedArchiveEntry(
                            entryName,
                            new ByteArrayInputStream(nestedArchive),
                            depth + 1,
                            budget);
                    if (nestedFinding.isPresent()) {
                        return Optional.of(entryName + "!/" + nestedFinding.get());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private byte[] readNestedArchive(InputStream archive, ArchiveScanBudget budget) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = archive.read(buffer)) != -1) {
            budget.recordExpandedBytes(read);
            if (output.size() + read > MAX_NESTED_ARCHIVE_BYTES) {
                throw new ArchiveScanLimitException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean isInspectableArchive(String filename) {
        if (filename == null) {
            return false;
        }
        String normalized = filename.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".zip")
                || normalized.endsWith(".tar")
                || normalized.endsWith(".tar.gz")
                || normalized.endsWith(".tgz")
                || normalized.endsWith(".tar.bz2")
                || normalized.endsWith(".tbz")
                || normalized.endsWith(".tbz2")
                || normalized.endsWith(".gz")
                || normalized.endsWith(".bz2");
    }

    private MimeBodyPart removalNotice(String filename, String prohibitedArchiveEntry) throws MessagingException {
        MimeBodyPart notice = new MimeBodyPart();
        notice.setText(
                removalNoticeText(filename, prohibitedArchiveEntry),
                StandardCharsets.UTF_8.name());
        return notice;
    }

    private String removalNoticeText(String filename, String prohibitedArchiveEntry) {
        String archiveDetail = prohibitedArchiveEntry == null
                ? ""
                : " The archive contained the prohibited file \"" + prohibitedArchiveEntry + "\".";
        return "InboxBridge removed the attachment \"" + filename
                + "\" because Gmail rejected it under the Gmail attachment security policy."
                + archiveDetail;
    }

    private boolean isProhibitedFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        int extensionSeparator = filename.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == filename.length() - 1) {
            return false;
        }
        return PROHIBITED_EXTENSIONS.contains(
                filename.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT));
    }

    public record SanitizedMessage(
            byte[] rawMessage,
            List<String> removedAttachmentNames) {

        public SanitizedMessage {
            rawMessage = rawMessage.clone();
            removedAttachmentNames = List.copyOf(removedAttachmentNames);
        }

        @Override
        public byte[] rawMessage() {
            return rawMessage.clone();
        }
    }

    private static final class ArchiveScanBudget {
        private int entries;
        private long expandedBytes;

        private void recordEntry() throws ArchiveScanLimitException {
            entries++;
            if (entries > MAX_ARCHIVE_ENTRIES) {
                throw new ArchiveScanLimitException();
            }
        }

        private void recordExpandedBytes(int bytes) throws ArchiveScanLimitException {
            expandedBytes += bytes;
            if (expandedBytes > MAX_ARCHIVE_EXPANDED_BYTES) {
                throw new ArchiveScanLimitException();
            }
        }
    }

    private static final class ArchiveBudgetInputStream extends FilterInputStream {
        private final ArchiveScanBudget budget;

        private ArchiveBudgetInputStream(InputStream input, ArchiveScanBudget budget) {
            super(input);
            this.budget = budget;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value != -1) {
                budget.recordExpandedBytes(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = in.read(buffer, offset, length);
            if (read > 0) {
                budget.recordExpandedBytes(read);
            }
            return read;
        }

        @Override
        public long skip(long count) throws IOException {
            long skipped = in.skip(count);
            if (skipped > 0) {
                if (skipped > Integer.MAX_VALUE) {
                    throw new ArchiveScanLimitException();
                }
                budget.recordExpandedBytes((int) skipped);
            }
            return skipped;
        }
    }

    private static final class ArchiveScanLimitException extends IOException {
    }
}
