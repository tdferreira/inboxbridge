package dev.inboxbridge.domain;

public record ImapCheckpoint(
        String folderName,
        Long uidValidity,
        Long lastSeenUid) {

    public boolean matchesFolder(String folder) {
        if (folderName == null || folder == null) {
            return false;
        }
        return folderName.equalsIgnoreCase(folder);
    }
}
