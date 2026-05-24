package dev.inboxbridge.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class MailboxFolderRoleDetector {

    private MailboxFolderRoleDetector() {
    }

    public static Optional<String> suggestSpamJunkFolder(List<String> folders) {
        if (folders == null || folders.isEmpty()) {
            return Optional.empty();
        }
        return folders.stream()
                .filter(folder -> folder != null && !folder.isBlank())
                .filter(MailboxFolderRoleDetector::isLikelySpamOrJunkFolder)
                .findFirst();
    }

    public static boolean isLikelySpamOrJunkFolder(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return false;
        }
        return Arrays.stream(folderName.split("[/\\\\.]"))
                .map(MailboxFolderRoleDetector::normalizeFolderToken)
                .anyMatch(SPAM_OR_JUNK_FOLDER_NAMES::contains)
                || SPAM_OR_JUNK_FOLDER_NAMES.contains(normalizeFolderToken(folderName));
    }

    public static boolean sameFolder(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalizeFolderName(left).equals(normalizeFolderName(right));
    }

    public static String normalizeFolderName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeFolderToken(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static final Set<String> SPAM_OR_JUNK_SPECIAL_USE_ATTRIBUTES = Set.of(
            "\\junk",
            "\\spam");

    private static final Set<String> SPAM_OR_JUNK_FOLDER_NAMES = Set.of(
            "spam",
            "junk",
            "junkemail",
            "junkeemail",
            "junkmail",
            "bulkmail",
            "correonodeseado",
            "correoindeseado",
            "indesejados");
}
