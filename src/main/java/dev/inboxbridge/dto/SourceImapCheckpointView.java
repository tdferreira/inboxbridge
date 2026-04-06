package dev.inboxbridge.dto;

import java.time.Instant;

/**
 * Read-only IMAP checkpoint details for one watched source folder.
 */
public record SourceImapCheckpointView(
        String folderName,
        Long uidValidity,
        Long lastSeenUid,
        Instant updatedAt) {
}
