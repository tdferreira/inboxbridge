package dev.inboxbridge.testsupport;

import java.util.logging.Level;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;

/**
 * Temporarily raises a logger threshold for a narrowly scoped test block so
 * expected recovery-path warnings do not drown out unexpected suite output.
 */
public final class ScopedLogSilencer implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;

    private ScopedLogSilencer(Logger logger, Level previousLevel) {
        this.logger = logger;
        this.previousLevel = previousLevel;
    }

    public static ScopedLogSilencer suppressWarnings(Class<?> loggerClass) {
        Logger logger = LogContext.getLogContext().getLogger(loggerClass.getName());
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.SEVERE);
        return new ScopedLogSilencer(logger, previousLevel);
    }

    public static ScopedLogSilencer suppressAll(Class<?> loggerClass) {
        Logger logger = LogContext.getLogContext().getLogger(loggerClass.getName());
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.OFF);
        return new ScopedLogSilencer(logger, previousLevel);
    }

    @Override
    public void close() {
        logger.setLevel(previousLevel);
    }
}
