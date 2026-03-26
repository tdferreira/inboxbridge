package dev.connexa.inboxbridge.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PollRunResult {

    private final Instant startedAt = Instant.now();
    private Instant finishedAt;
    private int fetched;
    private int imported;
    private int duplicates;
    private final List<String> errors = new ArrayList<>();

    public void incrementFetched() {
        fetched++;
    }

    public void incrementImported() {
        imported++;
    }

    public void incrementDuplicate() {
        duplicates++;
    }

    public void addError(String error) {
        errors.add(error);
    }

    public void finish() {
        finishedAt = Instant.now();
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

    public List<String> getErrors() {
        return errors;
    }
}
