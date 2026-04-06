package dev.inboxbridge.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;

/**
 * Normalizes configured source mailbox folders.
 *
 * <p>IMAP sources may now target multiple folders by storing a comma-separated
 * list in the existing folder field. POP3 remains effectively INBOX-only.
 */
public final class SourceMailboxFolders {

    private SourceMailboxFolders() {
    }

    public static List<String> forSource(InboxBridgeConfig.Protocol protocol, Optional<String> configuredFolders) {
        if (protocol == InboxBridgeConfig.Protocol.POP3) {
            return List.of("INBOX");
        }
        LinkedHashMap<String, String> foldersByKey = new LinkedHashMap<>();
        for (String token : split(configuredFolders.orElse(null))) {
            String normalized = normalize(token);
            if (normalized == null) {
                continue;
            }
            foldersByKey.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
        if (foldersByKey.isEmpty()) {
            return List.of("INBOX");
        }
        return List.copyOf(foldersByKey.values());
    }

    public static String primary(InboxBridgeConfig.Protocol protocol, Optional<String> configuredFolders) {
        return forSource(protocol, configuredFolders).getFirst();
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] tokens = value.split("[,\\n\\r]+");
        List<String> folders = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            folders.add(token);
        }
        return folders;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
