package dev.inboxbridge.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;

/**
 * Captures warning/error records for one logger during a narrow test scope and
 * temporarily disables parent-handler propagation so expected negative-path
 * logs do not pollute Maven output.
 */
public final class ScopedLogCapture implements AutoCloseable {

    private final Logger logger;
    private final boolean previousUseParentHandlers;
    private final Handler handler;
    private final List<CapturedRecord> records = new ArrayList<>();

    private ScopedLogCapture(Logger logger) {
        this.logger = logger;
        this.previousUseParentHandlers = logger.getUseParentHandlers();
        this.handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record == null || !isLoggable(record)) {
                    return;
                }
                records.add(new CapturedRecord(record.getLevel(), record.getMessage(), record.getThrown()));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        this.handler.setLevel(Level.WARNING);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
    }

    public static ScopedLogCapture captureWarnings(Class<?> loggerClass) {
        Logger logger = LogContext.getLogContext().getLogger(loggerClass.getName());
        return new ScopedLogCapture(logger);
    }

    public List<CapturedRecord> records() {
        return List.copyOf(records);
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
        logger.setUseParentHandlers(previousUseParentHandlers);
    }

    public record CapturedRecord(Level level, String message, Throwable thrown) {
    }
}
