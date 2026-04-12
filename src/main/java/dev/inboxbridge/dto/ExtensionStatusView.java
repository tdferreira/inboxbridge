package dev.inboxbridge.dto;

import java.util.List;

public record ExtensionStatusView(
        ExtensionUserView user,
        ExtensionPollStateView poll,
        ExtensionSummaryView summary,
        List<ExtensionSourceStatusView> sources) {
}
