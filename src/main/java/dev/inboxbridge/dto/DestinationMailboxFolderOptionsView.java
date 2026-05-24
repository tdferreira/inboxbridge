package dev.inboxbridge.dto;

import java.util.List;

import dev.inboxbridge.domain.MailboxFolderRoleDetector;

public record DestinationMailboxFolderOptionsView(
        List<String> folders,
        String suggestedSpamJunkFolder,
        List<String> suggestedSpamJunkFolders) {

    public DestinationMailboxFolderOptionsView(List<String> folders) {
        this(
                folders,
                MailboxFolderRoleDetector.suggestSpamJunkFolder(folders).orElse(null),
                MailboxFolderRoleDetector.suggestSpamJunkFolders(folders));
    }

    public DestinationMailboxFolderOptionsView(List<String> folders, String suggestedSpamJunkFolder) {
        this(
                folders,
                suggestedSpamJunkFolder,
                MailboxFolderRoleDetector.suggestSpamJunkFolders(folders));
    }
}
