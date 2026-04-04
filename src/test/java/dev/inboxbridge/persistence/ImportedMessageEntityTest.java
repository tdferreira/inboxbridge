package dev.inboxbridge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

class ImportedMessageEntityTest {

    @Test
    void importedMessageUsesDestinationScopedUniqueness() {
        Table table = ImportedMessage.class.getAnnotation(Table.class);

        List<UniqueConstraint> constraints = Arrays.asList(table.uniqueConstraints());
        assertTrue(constraints.stream().anyMatch((constraint) -> "uk_imported_message_destination_source_key".equals(constraint.name())
                && Arrays.equals(new String[] { "destination_key", "source_account_id", "source_message_key" }, constraint.columnNames())));
        assertTrue(constraints.stream().anyMatch((constraint) -> "uk_imported_message_destination_sha".equals(constraint.name())
                && Arrays.equals(new String[] { "destination_key", "raw_sha256" }, constraint.columnNames())));
    }

    @Test
    void importedMessageKeepsDestinationIndex() {
        Table table = ImportedMessage.class.getAnnotation(Table.class);

        List<Index> indexes = Arrays.asList(table.indexes());
        assertEquals("destination_key", indexes.stream()
                .filter((index) -> "idx_imported_message_destination".equals(index.name()))
                .findFirst()
                .orElseThrow()
                .columnList());
    }
}
