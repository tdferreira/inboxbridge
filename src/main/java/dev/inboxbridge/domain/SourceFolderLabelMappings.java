package dev.inboxbridge.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record SourceFolderLabelMappings(Map<String, String> labelsByFolderKey) {

    public static SourceFolderLabelMappings empty() {
        return new SourceFolderLabelMappings(Map.of());
    }

    public static SourceFolderLabelMappings parse(Optional<String> rawMappings) {
        return parse(rawMappings.orElse(""));
    }

    public static SourceFolderLabelMappings parse(String rawMappings) {
        if (rawMappings == null || rawMappings.isBlank()) {
            return empty();
        }
        Map<String, String> labelsByFolderKey = new LinkedHashMap<>();
        for (String entry : rawMappings.split("[;\\r\\n]+")) {
            String trimmedEntry = entry.trim();
            if (trimmedEntry.isEmpty()) {
                continue;
            }
            int separator = trimmedEntry.indexOf('=');
            if (separator <= 0 || separator == trimmedEntry.length() - 1) {
                throw new IllegalArgumentException("Folder label mappings must use folder=Gmail/Label entries.");
            }
            String folder = trimmedEntry.substring(0, separator).trim();
            String label = trimmedEntry.substring(separator + 1).trim();
            if (folder.isEmpty() || label.isEmpty()) {
                throw new IllegalArgumentException("Folder label mappings must use folder=Gmail/Label entries.");
            }
            String folderKey = normalizeFolder(folder);
            if (labelsByFolderKey.containsKey(folderKey)) {
                throw new IllegalArgumentException("Folder label mappings contain the same source folder more than once: " + folder);
            }
            labelsByFolderKey.put(folderKey, label);
        }
        return new SourceFolderLabelMappings(Collections.unmodifiableMap(new LinkedHashMap<>(labelsByFolderKey)));
    }

    public Optional<String> labelFor(Optional<String> folderName) {
        return folderName
                .map(SourceFolderLabelMappings::normalizeFolder)
                .map(labelsByFolderKey::get)
                .filter(label -> !label.isBlank());
    }

    public boolean isEmpty() {
        return labelsByFolderKey.isEmpty();
    }

    private static String normalizeFolder(String folderName) {
        return folderName.trim().toLowerCase(Locale.ROOT);
    }
}
