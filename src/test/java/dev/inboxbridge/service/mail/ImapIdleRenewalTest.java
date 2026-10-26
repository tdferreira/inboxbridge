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
    void closeAtEachSchedulingBoundarySuppressesLateCallbacks() {
        for(String boundary:java.util.List.of("sleep","success","failure","acknowledgement")) {
            var loop=new AtomicReference<Runnable>();var holder=new AtomicReference<ImapIdleRenewal>();
            var callbacks=new AtomicInteger();var started=new AtomicInteger();
            java.util.function.Function<Runnable,Thread> scheduler=task->{
                if(started.getAndIncrement()==0) loop.set(task);
                else { task.run(); if(!boundary.equals("acknowledgement")) holder.get().close(); }
                return new Thread(){ @Override public void interrupt(){} };
            };
            var renewal=new ImapIdleRenewal(Duration.ofMillis(1),Duration.ofSeconds(1),
                ()->{if(boundary.equals("failure"))throw new IllegalStateException("synthetic close");},
                ()->{callbacks.incrementAndGet();holder.get().close();},error->callbacks.incrementAndGet(),
                duration->{if(boundary.equals("sleep"))holder.get().close();},scheduler);
            holder.set(renewal);loop.get().run();
            assertEquals(boundary.equals("acknowledgement")?1:0,callbacks.get());
            renewal.close();
        }
    }

    @Test
    void closeBeforeScheduledLoopStartsDoesNotStartACommand() {
        var loop=new AtomicReference<Runnable>();var commands=new AtomicInteger();
        var renewal=new ImapIdleRenewal(Duration.ofMillis(1),Duration.ofSeconds(1),commands::incrementAndGet,
            ()->fail("No acknowledgement expected"),error->fail("No failure expected"),duration->{},
            task->{loop.set(task);return new Thread(){@Override public void interrupt(){}};});
        renewal.close();loop.get().run();assertEquals(0,commands.get());
    }

    @Test
    void validationRejectsEveryNonPositiveOrMissingDuration() {
        for(Duration interval:new Duration[]{null,Duration.ZERO,Duration.ofNanos(-1)})
            assertThrows(IllegalArgumentException.class,()->ImapIdleRenewal.validate(interval,Duration.ofSeconds(1)));
        for(Duration timeout:new Duration[]{null,Duration.ZERO,Duration.ofNanos(-1)})
            assertThrows(IllegalArgumentException.class,()->ImapIdleRenewal.validate(Duration.ofSeconds(1),timeout));
    }

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
