package dev.inboxbridge.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PollRunResult {

    private final Instant startedAt = Instant.now();
    private Instant finishedAt;
    private int fetched;
    private int imported;
    private long importedBytes;
    private int duplicates;
    private int spamJunkMessageCount;
    private final List<String> errors = new ArrayList<>();
    private final List<PollRunError> errorDetails = new ArrayList<>();
    private final List<String> spamJunkFolderSummaries = new ArrayList<>();
    private String state = "COMPLETED";
    private String stoppedByUsername;

    public void incrementFetched() {
        fetched++;
    }

    public void incrementImported() {
        imported++;
    }

    public void addImportedBytes(long bytes) {
        importedBytes += Math.max(0L, bytes);
    }

    public void incrementDuplicate() {
        duplicates++;
    }

    public void addFetched(int count) {
        fetched += Math.max(0, count);
    }

    public void addImported(int count) {
        imported += Math.max(0, count);
    }

    public void addDuplicates(int count) {
        duplicates += Math.max(0, count);
    }

    public void addSpamJunkMessageCount(int count) {
        spamJunkMessageCount += Math.max(0, count);
    }

    public void addError(String error) {
        errors.add(error);
    }

    public void addError(PollRunError error) {
        if (error == null) {
            return;
        }
        errorDetails.add(error);
        if (error.message() != null && !error.message().isBlank()) {
            errors.add(error.message());
        }
    }

    public void addSpamJunkFolderSummary(String sourceId, String folderName, int messageCount) {
        spamJunkFolderSummaries.add(sourceId + " -> " + folderName + " (" + messageCount + ")");
    }

    public void addSpamJunkFolderSummary(String summary) {
        if (summary != null && !summary.isBlank()) {
            spamJunkFolderSummaries.add(summary);
        }
    }

    public void finish() {
        finishedAt = Instant.now();
    }

    public void markStopped(String username) {
        state = "STOPPED";
        stoppedByUsername = username;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getFetched() {
        return fetched;
    }

    public int getImported() {
        return imported;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public long getImportedBytes() {
        return importedBytes;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<PollRunError> getErrorDetails() {
        return errorDetails;
    }

    public int getSpamJunkMessageCount() {
        return spamJunkMessageCount;
    }

    public List<String> getSpamJunkFolderSummaries() {
        return spamJunkFolderSummaries;
    }

    public String getState() {
        return state;
    }

    public String getStoppedByUsername() {
        return stoppedByUsername;
    }
}
