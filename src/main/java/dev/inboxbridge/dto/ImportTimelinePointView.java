package dev.inboxbridge.dto;

/**
 * Represents imported-message volume for one time bucket so the admin UI can
 * visualize recent activity without exposing individual message content.
 */
public record ImportTimelinePointView(
        String bucketLabel,
        long importedMessages) {
}
