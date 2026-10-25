package dev.inboxbridge.service.mail;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class ImapIdleRenewalTest {
    @Test
    void renewsRepeatedlyAndStopsOnClose() throws Exception {
        CountDownLatch renewed = new CountDownLatch(2);
        AtomicInteger commands = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (var renewal = new ImapIdleRenewal(Duration.ofMillis(20), Duration.ofSeconds(1),
                commands::incrementAndGet, renewed::countDown, failure::set)) {
            assertTrue(renewed.await(3, TimeUnit.SECONDS));
        }
        assertTrue(commands.get() >= 2);
        assertNull(failure.get());
    }

    @Test
    void hungCommandTriggersRecoveryAndIsInterrupted() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger catchUps = new AtomicInteger();
        try (var renewal = new ImapIdleRenewal(Duration.ofMillis(10), Duration.ofMillis(100), () -> {
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        }, catchUps::incrementAndGet, error -> failed.countDown())) {
            assertTrue(failed.await(3, TimeUnit.SECONDS));
            assertTrue(interrupted.await(3, TimeUnit.SECONDS));
            assertEquals(0, catchUps.get());
        }
    }

    @Test
    void protocolFailureTriggersRecoveryInsteadOfSuccessfulRenewal() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        AtomicInteger catchUps = new AtomicInteger();
        try (var renewal = new ImapIdleRenewal(Duration.ofMillis(10), Duration.ofSeconds(1),
                () -> { throw new IllegalStateException("test connection closed"); },
                catchUps::incrementAndGet, error -> failed.countDown())) {
            assertTrue(failed.await(3, TimeUnit.SECONDS));
            assertEquals(0, catchUps.get());
        }
    }

    @Test
    void closingBeforeTheDeadlineDoesNotRequestRecovery() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        var renewal = new ImapIdleRenewal(Duration.ofMillis(10), Duration.ofSeconds(5), () -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException expected) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        }, callbacks::incrementAndGet, error -> callbacks.incrementAndGet());
        assertTrue(started.await(3, TimeUnit.SECONDS));
        renewal.close();
        assertTrue(interrupted.await(3, TimeUnit.SECONDS));
        assertEquals(0, callbacks.get());
    }

    @Test
    void rejectsUnboundedOrLateRenewalConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> ImapIdleRenewal.validate(Duration.ZERO, Duration.ofSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> ImapIdleRenewal.validate(Duration.ofMinutes(29), Duration.ofSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> ImapIdleRenewal.validate(Duration.ofMinutes(25), Duration.ZERO));
    }
}
