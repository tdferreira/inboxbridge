package dev.inboxbridge.dto;

import java.util.List;

/**
 * Reports the health of one concrete component involved in the currently
 * selected secret-management provider path.
 */
public record SecretProviderComponentStatusView(
        String componentId,
        String title,
        String detail,
        List<String> configReferences,
        boolean healthy,
        boolean writable) {
}
