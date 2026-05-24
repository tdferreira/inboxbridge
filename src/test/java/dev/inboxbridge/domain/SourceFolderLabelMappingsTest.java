package dev.inboxbridge.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class SourceFolderLabelMappingsTest {

    @Test
    void parsesLineAndSemicolonSeparatedMappingsCaseInsensitively() {
        SourceFolderLabelMappings mappings = SourceFolderLabelMappings.parse("""
                INBOX=Imported/Inbox
                Projects/2026=Imported/Projects;Junk=SPAM
                """);

        assertEquals("Imported/Inbox", mappings.labelFor(Optional.of("inbox")).orElseThrow());
        assertEquals("Imported/Projects", mappings.labelFor(Optional.of("projects/2026")).orElseThrow());
        assertEquals("SPAM", mappings.labelFor(Optional.of("Junk")).orElseThrow());
    }

    @Test
    void rejectsMalformedEntries() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SourceFolderLabelMappings.parse("INBOX"));

        assertEquals("Folder label mappings must use folder=Gmail/Label entries.", error.getMessage());
    }

    @Test
    void rejectsDuplicateFolders() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SourceFolderLabelMappings.parse("INBOX=Imported/Inbox;inbox=Imported/Other"));

        assertEquals("Folder label mappings contain the same source folder more than once: inbox", error.getMessage());
    }

    @Test
    void emptyInputReturnsNoMapping() {
        SourceFolderLabelMappings mappings = SourceFolderLabelMappings.parse(Optional.empty());

        assertTrue(mappings.isEmpty());
        assertTrue(mappings.labelFor(Optional.of("INBOX")).isEmpty());
    }
}
