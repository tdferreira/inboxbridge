package dev.inboxbridge.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;

/**
 * Owns source-mailbox folder discovery and spam-folder probing so those rules
 * stay independent from polling orchestration.
 */
@ApplicationScoped
public class MailSourceFolderService {

    public List<String> listFolders(Store store) throws MessagingException {
        LinkedHashSet<String> folderNames = new LinkedHashSet<>();
        Folder inbox = store.getFolder("INBOX");
        if (inbox != null && inbox.exists()) {
            folderNames.add(inbox.getFullName());
        }

        Folder defaultFolder = store.getDefaultFolder();
        if (defaultFolder != null) {
            collectFolderNames(defaultFolder.list("*"), folderNames);
        }

        List<String> folders = new ArrayList<>(folderNames);
        folders.sort(Comparator
                .comparing((String folderName) -> !"INBOX".equalsIgnoreCase(folderName))
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        return folders;
    }

    public Optional<MailSourceClient.MailboxCountProbe> probeSpamOrJunkFolder(Store store) throws MessagingException {
        Folder folder = locateSpamOrJunkFolder(store);
        if (folder == null || !folder.exists()) {
            return Optional.empty();
        }
        folder.open(Folder.READ_ONLY);
        try {
            return Optional.of(new MailSourceClient.MailboxCountProbe(folder.getFullName(), folder.getMessageCount()));
        } finally {
            closeQuietly(folder);
        }
    }

    public Folder locateSpamOrJunkFolder(Store store) throws MessagingException {
        Folder defaultFolder = store.getDefaultFolder();
        if (defaultFolder == null) {
            return null;
        }
        List<Folder> folders = new ArrayList<>();
        collectFolders(defaultFolder, folders, new HashSet<>());
        for (Folder folder : folders) {
            if (hasSpamOrJunkSpecialUse(folder)) {
                return folder;
            }
        }
        for (Folder folder : folders) {
            if (isLikelySpamOrJunkFolder(folder)) {
                return folder;
            }
        }
        return null;
    }

    static Boolean resolveForwardedMarkerSupport(Folder folder) {
        if (folder == null) {
            return null;
        }
        Flags permanentFlags = folder.getPermanentFlags();
        if (permanentFlags == null) {
            return null;
        }
        String[] userFlags = permanentFlags.getUserFlags();
        if (userFlags != null) {
            for (String userFlag : userFlags) {
                if ("$forwarded".equalsIgnoreCase(userFlag)) {
                    return Boolean.TRUE;
                }
            }
        }
        if (permanentFlags.contains(Flags.Flag.USER)) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    private void collectFolderNames(Folder[] folders, LinkedHashSet<String> names) throws MessagingException {
        if (folders == null) {
            return;
        }
        for (Folder folder : folders) {
            if (folder == null) {
                continue;
            }
            if (folder.exists()) {
                String fullName = folder.getFullName();
                if (fullName != null && !fullName.isBlank()) {
                    names.add(fullName);
                }
            }
            if ((folder.getType() & Folder.HOLDS_FOLDERS) != 0) {
                collectFolderNames(folder.list("*"), names);
            }
        }
    }

    private void collectFolders(Folder folder, List<Folder> collected, Set<String> visited) throws MessagingException {
        String fullName = folder.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            if (!visited.add(fullName)) {
                return;
            }
            collected.add(folder);
        }
        for (Folder child : folder.list("*")) {
            collectFolders(child, collected, visited);
        }
    }

    private boolean isLikelySpamOrJunkFolder(Folder folder) {
        char separator;
        try {
            separator = folder.getSeparator();
        } catch (MessagingException e) {
            separator = '/';
        }
        List<String> candidates = new ArrayList<>();
        if (folder.getFullName() != null) {
            candidates.add(folder.getFullName());
            if (separator != 0) {
                candidates.addAll(Arrays.asList(folder.getFullName().split(java.util.regex.Pattern.quote(String.valueOf(separator)))));
            }
        }
        if (folder.getName() != null) {
            candidates.add(folder.getName());
        }
        return candidates.stream()
                .map(this::normalizeFolderToken)
                .anyMatch(SPAM_OR_JUNK_FOLDER_NAMES::contains);
    }

    private boolean hasSpamOrJunkSpecialUse(Folder folder) {
        try {
            java.lang.reflect.Method method = folder.getClass().getMethod("getAttributes");
            Object result = method.invoke(folder);
            if (!(result instanceof String[] attributes)) {
                return false;
            }
            for (String attribute : attributes) {
                String normalized = String.valueOf(attribute).trim().toLowerCase(Locale.ROOT);
                if (SPAM_OR_JUNK_SPECIAL_USE_ATTRIBUTES.contains(normalized)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private String normalizeFolderToken(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void closeQuietly(Folder folder) {
        if (folder == null) {
            return;
        }
        try {
            if (folder.isOpen()) {
                folder.close(false);
            }
        } catch (MessagingException ignored) {
        }
    }

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

    private static final Set<String> SPAM_OR_JUNK_SPECIAL_USE_ATTRIBUTES = Set.of(
            "\\junk",
            "\\spam");
}
