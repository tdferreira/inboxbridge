package dev.inboxbridge.dto;

import java.util.List;

import dev.inboxbridge.domain.MailboxFolderRoleDetector;

public record DestinationMailboxFolderOptionsView(
        List<String> folders,
        String suggestedSpamJunkFolder) {

    public DestinationMailboxFolderOptionsView(List<String> folders) {
        this(folders, MailboxFolderRoleDetector.suggestSpamJunkFolder(folders).orElse(null));
    }
}
