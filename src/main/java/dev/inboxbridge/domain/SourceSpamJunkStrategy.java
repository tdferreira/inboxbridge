package dev.inboxbridge.domain;

public enum SourceSpamJunkStrategy {
    IGNORE(false, false),
    IMPORT_NORMAL(true, false),
    IMPORT_AND_ROUTE(true, true);

    private final boolean importsSpamJunk;
    private final boolean routesSpamJunk;

    SourceSpamJunkStrategy(boolean importsSpamJunk, boolean routesSpamJunk) {
        this.importsSpamJunk = importsSpamJunk;
        this.routesSpamJunk = routesSpamJunk;
    }

    public boolean importsSpamJunk() {
        return importsSpamJunk;
    }

    public boolean routesSpamJunk() {
        return routesSpamJunk;
    }

    public static SourceSpamJunkStrategy from(String value) {
        if (value == null || value.isBlank()) {
            return IGNORE;
        }
        return SourceSpamJunkStrategy.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
