package dev.inboxbridge.service.mail;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Ends IDLE through a normal folder command on a separate virtual thread.
 * A deadline also bounds a silent server that never acknowledges DONE/NOOP.
 * One renewal loop per folder prevents a stalled mailbox from blocking others.
 */
final class ImapIdleRenewal implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread renewalThread;
    private volatile Thread commandThread;

    ImapIdleRenewal(Duration interval, Duration timeout, Runnable command,
            Runnable renewed, Consumer<Throwable> failed) {
        validate(interval, timeout);
        renewalThread = Thread.startVirtualThread(() -> {
            while (!closed.get()) {
                try {
                    Thread.sleep(interval);
                    if (closed.get()) {
                        return;
                    }
                    FutureTask<Void> attempt = new FutureTask<>(command, null);
                    commandThread = Thread.startVirtualThread(attempt);
                    try {
                        attempt.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
                        if (!closed.get()) {
                            renewed.run();
                        }
                    } catch (ExecutionException | TimeoutException error) {
                        if (!closed.get()) {
                            failed.accept(error instanceof ExecutionException ? error.getCause() : error);
                        }
                        return;
                    } finally {
                        attempt.cancel(true);
                        commandThread.interrupt();
                        commandThread = null;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
    }

    static void validate(Duration interval, Duration timeout) {
        if (interval == null || timeout == null || interval.isZero() || interval.isNegative()
                || timeout.isZero() || timeout.isNegative()
                || interval.plus(timeout).compareTo(Duration.ofMinutes(29)) >= 0) {
            throw new IllegalArgumentException("IDLE renewal interval and timeout must be positive and total less than 29 minutes");
        }
    }

    @Override
    public void close() {
        closed.set(true);
        renewalThread.interrupt();
        Thread currentCommand = commandThread;
        if (currentCommand != null) {
            currentCommand.interrupt();
        }
    }
}
