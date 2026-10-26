package dev.inboxbridge.service.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.angus.mail.imap.IMAPFolder;
import org.eclipse.angus.mail.imap.protocol.IMAPProtocol;
import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.dto.PollRunError;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.service.polling.PollingService;
import dev.inboxbridge.service.polling.PollingSettingsService;
import dev.inboxbridge.service.polling.SourcePollingSettingsService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.MessagingException;

/** Isolated orchestration tests: no CDI runtime, socket, database or mail server. */
class ImapIdleWatchServiceTest {
    @Test
    void reconnectCatchUpIsCoalescedAndNotificationTakesPriority() throws Exception {
        var f = new Fixture();
        invoke(f.service, "queueSync", new Class<?>[]{String.class, boolean.class}, "source", false);
        invoke(f.service, "queueSync", new Class<?>[]{String.class, boolean.class}, "source", false);
        invoke(f.service, "queueSync", new Class<?>[]{String.class, boolean.class}, "source", true);
        invoke(f.service, "queueSync", new Class<?>[]{String.class, boolean.class}, "source", false);
        f.service.drainPendingSources();
        verify(f.service.pollingService).runIdleTriggeredPollForSource(f.account);
        verify(f.service.pollingService, never()).runIdleCatchUpForSource(any());
        f.service.drainPendingSources();
        verifyNoMoreInteractions(f.service.pollingService);
    }

    @Test
    void busyCatchUpIsRetriedAndRemovedMailboxIsDropped() throws Exception {
        var f = new Fixture();
        var busy = new PollRunResult(); busy.addError(new PollRunError("poll_busy", "source", "busy", null));
        when(f.service.pollingService.runIdleCatchUpForSource(f.account)).thenReturn(busy, new PollRunResult());
        invoke(f.service, "queueSync", new Class<?>[]{String.class, boolean.class}, "source", false);
        f.service.drainPendingSources(); f.service.drainPendingSources();
        verify(f.service.pollingService, times(2)).runIdleCatchUpForSource(f.account);
        invoke(f.service, "queueSync", new Class<?>[]{String.class, boolean.class}, "removed", true);
        f.service.drainPendingSources();
        assertTrue(pending(f.service).isEmpty());
        f.service.pollingService = null; f.service.drainPendingSources();
    }

    @Test
    void busyDetectionRequiresMatchingSourceAndAcceptsGlobalBusy() throws Exception {
        var f = new Fixture();
        assertFalse((boolean) invoke(f.service, "isBusyResult", new Class<?>[]{PollRunResult.class,String.class}, null,"source"));
        for (String code : List.of("poll_busy", "source_failed")) {
            var result = new PollRunResult(); result.addError(new PollRunError(code,"other","test",null));
            assertFalse((boolean) invoke(f.service,"isBusyResult",new Class<?>[]{PollRunResult.class,String.class},result,"source"));
            assertEquals(code.equals("poll_busy"), invoke(f.service,"isBusyResult",new Class<?>[]{PollRunResult.class,String.class},result,null));
        }
    }

    @Test
    void renewalAcknowledgementUpdatesHealthWithoutQueueingAnImportAndRejectsStaleCallbacks() throws Exception {
        var f = new Fixture(); var handle = f.handle();
        try (var constructions = mockConstruction(ImapIdleRenewal.class, (mock, context) -> f.callbacks.set(context.arguments()))) {
            doAnswer(call -> {
                pending(f.service).clear(); // Discard initial connection catch-up.
                ((Runnable) f.callbacks.get().get(2)).run();
                Runnable acknowledge = (Runnable) f.callbacks.get().get(3);
                acknowledge.run();
                assertNotNull(f.service.imapIdleHealthService.watchStates("source").getFirst().lastRenewedAt());
                assertTrue(pending(f.service).isEmpty(), "A renewal must not become a mail notification");
                set(handle,"folder",null); acknowledge.run(); // Old session cannot renew its replacement.
                set(handle,"folder",f.folder); active(handle).set(false); acknowledge.run();
                return null;
            }).when(f.folder).idle();
            invoke(handle,"watchUntilInterrupted",new Class<?>[]{});
            verify(constructions.constructed().getFirst()).close();
        }
    }

    @Test
    void failedRenewalDisconnectsAndInterruptsOnlyItsReaderAndIgnoresStaleCallbacks() throws Exception {
        var f = new Fixture(); var handle=f.handle(); var interrupts=new AtomicInteger();
        set(handle,"thread",new Thread(){ @Override public void interrupt(){interrupts.incrementAndGet();} });
        try(var ignored=mockConstruction(ImapIdleRenewal.class,(mock,ctx)->f.callbacks.set(ctx.arguments()))) {
            doAnswer(call->{
                @SuppressWarnings("unchecked") var failed=(Consumer<Throwable>) f.callbacks.get().get(4);
                failed.accept(new MessagingException("synthetic timeout"));
                assertFalse(f.service.imapIdleHealthService.isHealthy("source"));
                assertEquals(1,interrupts.get());
                set(handle,"folder",null); failed.accept(new MessagingException());
                set(handle,"folder",f.folder); active(handle).set(false); failed.accept(new MessagingException());
                assertEquals(1,interrupts.get());
                return null;
            }).when(f.folder).idle();
            invoke(handle,"watchUntilInterrupted",new Class<?>[]{});
        }
    }

    @Test
    void renewalRunsNoopAndWrapsProtocolFailure() throws Exception {
        var f=new Fixture(); var handle=f.handle(); var protocol=mock(IMAPProtocol.class);
        when(f.folder.doCommand(any())).thenAnswer(call->((IMAPFolder.ProtocolCommand)call.getArgument(0)).doCommand(protocol));
        invoke(handle,"renewIdle",new Class<?>[]{IMAPFolder.class},f.folder);
        verify(protocol).noop();
        doThrow(new MessagingException("synthetic close")).when(f.folder).doCommand(any());
        assertInstanceOf(MessagingException.class,assertThrows(IllegalStateException.class,
                ()->invoke(handle,"renewIdle",new Class<?>[]{IMAPFolder.class},f.folder)).getCause());
    }

    @Test
    void mailboxGrowthQueuesNotificationAndCleanupDisconnects() throws Exception {
        var f=new Fixture(); var handle=f.handle();
        when(f.folder.getMessageCount()).thenReturn(1,2);
        doAnswer(call->{ when(f.store.isConnected()).thenReturn(false); return null; }).when(f.folder).idle();
        try(var ignored=mockConstruction(ImapIdleRenewal.class)) {
            invoke(handle,"watchUntilInterrupted",new Class<?>[]{});
        }
        assertEquals(Boolean.TRUE,pending(f.service).get("source"));
        assertFalse(f.service.imapIdleHealthService.isHealthy("source"));
        verify(f.folder).close(false); verify(f.store).close();
    }

    @Test
    void missingFolderFailureStillCleansConnectionAndDoesNotCreateRenewal() throws Exception {
        var f=new Fixture(); when(f.folder.exists()).thenReturn(false);
        try(var constructions=mockConstruction(ImapIdleRenewal.class)) {
            assertThrows(IllegalStateException.class,()->invoke(f.handle(),"watchUntilInterrupted",new Class<?>[]{}));
            assertTrue(constructions.constructed().isEmpty());
        }
        verify(f.store).close();
    }

    @Test
    void refreshResolvesGlobalAndSourcePolicyAndRemovesChangedTargets() throws Exception {
        var f=new Fixture();
        f.service.pollingSettingsService=mock(PollingSettingsService.class);
        when(f.service.pollingSettingsService.effectiveIdleSettings()).thenReturn(
            new PollingSettingsService.EffectiveIdleSettings(true,Duration.ofMinutes(10),Duration.ofSeconds(20)));
        when(f.service.pollingSettingsService.effectiveIdleSafetyPollInterval()).thenReturn(Duration.ofMinutes(15));
        f.service.sourcePollingSettingsService=mock(SourcePollingSettingsService.class);
        when(f.service.sourcePollingSettingsService.effectiveIdleRenewalInterval(f.account)).thenReturn(Duration.ofMinutes(5));
        when(f.service.sourcePollingSettingsService.effectiveIdleSafetyPollInterval(f.account)).thenReturn(Duration.ofMinutes(15));
        // Hold the reader at the transport boundary; lifecycle and registry code remain real.
        var entered=new java.util.concurrent.CountDownLatch(1);
        doAnswer(call->{ entered.countDown(); awaitCancellation(); return null; }).when(f.folder).idle();
        try {
            f.service.onStart(null);
            assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
            f.service.refreshScheduled();
            var prior=((Map<?,?>)get(f.service,"handles")).values().iterator().next();
            when(f.service.sourcePollingSettingsService.effectiveIdleRenewalInterval(f.account)).thenReturn(Duration.ofMinutes(6));
            f.service.refreshScheduled();
            assertNotSame(prior,((Map<?,?>)get(f.service,"handles")).values().iterator().next());
            when(f.service.runtimeEmailAccountService.listEnabledForPolling()).thenReturn(List.of());
            f.service.onSourceMailboxConfigurationChanged(null);
            assertFalse(f.service.imapIdleHealthService.isHealthy("source"));
        } finally { f.service.shutdown(); }
    }

    @Test
    void fallbackFactoryUsesSafeDefaultsAndExecutionIntervalPreservesProductionPolicy() throws Exception {
        var service=new ImapIdleWatchService();
        var factory=(MailSessionFactory)invoke(service,"mailSessionFactory",new Class<?>[]{});
        assertEquals(Duration.ofMinutes(25),factory.mailClientConfig.idleRenewalInterval());
        assertEquals(Duration.ofSeconds(20),factory.mailClientConfig.idleRenewalTimeout());
        assertEquals(Duration.ofMinutes(30),factory.mailClientConfig.idleSafetyPollInterval());
        assertTrue(factory.mailClientConfig.idleSafetyPollingEnabled());
        assertEquals(Duration.ofMinutes(10),service.renewalExecutionInterval(Duration.ofMinutes(10)));
        var standalone=MailSourceStandaloneFactory.mailSessionFactory();
        assertEquals(Duration.ofMinutes(25),standalone.mailClientConfig.idleRenewalInterval());
        assertEquals(Duration.ofSeconds(20),standalone.mailClientConfig.idleRenewalTimeout());
        assertEquals(Duration.ofMinutes(30),standalone.mailClientConfig.idleSafetyPollInterval());
        assertTrue(standalone.mailClientConfig.idleSafetyPollingEnabled());
        var destination=new dev.inboxbridge.service.destination.ImapAppendMailDestinationService();
        var destinationMethod=destination.getClass().getDeclaredMethod("mailSessionFactory");destinationMethod.setAccessible(true);
        var destinationFactory=(MailSessionFactory)destinationMethod.invoke(destination);
        assertEquals(Duration.ofMinutes(25),destinationFactory.mailClientConfig.idleRenewalInterval());
        assertEquals(Duration.ofSeconds(20),destinationFactory.mailClientConfig.idleRenewalTimeout());
        assertEquals(Duration.ofMinutes(30),destinationFactory.mailClientConfig.idleSafetyPollInterval());
        assertTrue(destinationFactory.mailClientConfig.idleSafetyPollingEnabled());
        service.shutdown();
    }

    @Test
    void aSecondDrainerMayAlreadyHaveConsumedTheQueuedSource() throws Exception {
        var f=new Fixture();
        @SuppressWarnings("unchecked") Map<String,Boolean> queue=mock(Map.class);
        when(queue.keySet()).thenReturn(java.util.Set.of("source"));
        set(f.service,"pendingSources",queue);
        f.service.drainPendingSources();
        verifyNoInteractions(f.service.pollingService);
    }

    @Test
    void runLoopResetsAfterACompletedConnectionAndStopsAfterClosedFailure() throws Exception {
        var f=new Fixture(); var handle=f.handle();
        try(var ignored=mockConstruction(ImapIdleRenewal.class)) {
            invoke(handle,"runLoop",new Class<?>[]{});
        }
        assertFalse(active(handle).get());
        var failed=new Fixture(); var failedHandle=failed.handle();
        when(failed.folder.exists()).thenAnswer(call->{active(failedHandle).set(false);return false;});
        invoke(failedHandle,"runLoop",new Class<?>[]{});
    }

    @Test
    void messageListenerQueuesArrivalAndNullHealthStillAllowsRenewalCallbacks() throws Exception {
        var f=new Fixture(); var handle=f.handle(); f.service.imapIdleHealthService=null;
        set(handle,"thread",new Thread(){ @Override public void interrupt(){} });
        var listener=new AtomicReference<jakarta.mail.event.MessageCountListener>();
        doAnswer(call->{listener.set(call.getArgument(0));return null;}).when(f.folder).addMessageCountListener(any());
        try(var ignored=mockConstruction(ImapIdleRenewal.class,(mock,ctx)->f.callbacks.set(ctx.arguments()))) {
            doAnswer(call->{
                listener.get().messagesAdded(new jakarta.mail.event.MessageCountEvent(f.folder,
                    jakarta.mail.event.MessageCountEvent.ADDED,false,new jakarta.mail.Message[0]));
                ((Runnable)f.callbacks.get().get(3)).run();
                @SuppressWarnings("unchecked") var failure=(Consumer<Throwable>)f.callbacks.get().get(4);
                failure.accept(new MessagingException());
                active(handle).set(false);return null;
            }).when(f.folder).idle();
            invoke(handle,"watchUntilInterrupted",new Class<?>[]{});
        }
        assertEquals(Boolean.TRUE,pending(f.service).get("source"));
    }

    @Test
    void closedFolderOrInterruptedReaderDoesNotEnterIdle() throws Exception {
        for(boolean interrupted:List.of(false,true)) {
            var f=new Fixture(); var handle=f.handle();
            if(interrupted) Thread.currentThread().interrupt();
            else when(f.folder.isOpen()).thenReturn(false);
            try(var ignored=mockConstruction(ImapIdleRenewal.class)) {
                invoke(handle,"watchUntilInterrupted",new Class<?>[]{});
                verify(f.folder,never()).idle();
            } finally {Thread.interrupted();}
        }
    }

    @Test
    void liveTimingOrSourceIdentityChangesRequireReplacingTheWatch() throws Exception {
        var f=new Fixture(); var handle=f.handle();
        var types=new Class<?>[]{RuntimeEmailAccount.class,String.class,Duration.class};
        assertEquals(true,invoke(handle,"matches",types,f.account,"INBOX",Duration.ofMinutes(25)));
        assertEquals(false,invoke(handle,"matches",types,f.account,"INBOX",Duration.ofMinutes(10)));
        f.service.idleRenewalTimeout=Duration.ofSeconds(10);
        assertEquals(false,invoke(handle,"matches",types,f.account,"INBOX",Duration.ofMinutes(25)));
        f.service.idleRenewalTimeout=Duration.ofSeconds(20);
        var other=mock(RuntimeEmailAccount.class);when(other.id()).thenReturn("other");
        assertEquals(false,invoke(handle,"matches",types,other,"INBOX",Duration.ofMinutes(25)));
    }

    @Test
    void refreshWithoutSettingsUsesDefaultsAndShutdownClearsTheRegistry() throws Exception {
        var f=new Fixture();var entered=new java.util.concurrent.CountDownLatch(1);
        doAnswer(call->{entered.countDown();awaitCancellation();return null;}).when(f.folder).idle();
        try { f.service.refreshWatches(); assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS)); }
        finally {f.service.shutdown();}
        assertFalse(f.service.imapIdleHealthService.isHealthy("source"));
    }

    @Test
    void failedConnectionRetriesAndClearsThePriorInterruptionState() throws Exception {
        var f=new Fixture();var handle=f.handle();var attempts=new AtomicInteger();
        when(f.folder.exists()).thenAnswer(call->{if(attempts.incrementAndGet()==2)active(handle).set(false);return false;});
        invoke(handle,"runLoop",new Class<?>[]{});
        assertEquals(2,attempts.get());
    }

    private static class Fixture {
        final ImapIdleWatchService service=new ImapIdleWatchService();
        final RuntimeEmailAccount account=mock(RuntimeEmailAccount.class);
        final Session session=mock(Session.class);
        final Store store=mock(Store.class);
        final IMAPFolder folder=mock(IMAPFolder.class);
        final AtomicReference<List<?>> callbacks=new AtomicReference<>();
        Fixture() throws Exception {
            when(account.id()).thenReturn("source"); when(account.enabled()).thenReturn(true);
            when(account.protocol()).thenReturn(InboxBridgeConfig.Protocol.IMAP); when(account.fetchMode()).thenReturn(SourceFetchMode.IDLE);
            when(account.host()).thenReturn("imap.example.test"); when(account.port()).thenReturn(993);
            when(account.tls()).thenReturn(true); when(account.username()).thenReturn("synthetic");
            when(account.password()).thenReturn("synthetic"); when(account.oauthRefreshToken()).thenReturn("");
            when(account.authMethod()).thenReturn(InboxBridgeConfig.AuthMethod.PASSWORD);
            when(account.oauthProvider()).thenReturn(InboxBridgeConfig.OAuthProvider.NONE);
            when(account.sourceFolders()).thenReturn(List.of("INBOX"));
            service.runtimeEmailAccountService=mock(RuntimeEmailAccountService.class);
            when(service.runtimeEmailAccountService.listEnabledForPolling()).thenReturn(List.of(account));
            service.pollingService=mock(PollingService.class); service.imapIdleHealthService=new ImapIdleHealthService();
            service.mailSessionFactory=mock(MailSessionFactory.class);
            when(service.mailSessionFactory.idleImapSession(account)).thenReturn(session);
            when(service.mailSessionFactory.imapStoreProtocol(true)).thenReturn("imaps");
            when(session.getStore("imaps")).thenReturn(store); when(store.getFolder("INBOX")).thenReturn(folder);
            when(folder.exists()).thenReturn(true); when(folder.isOpen()).thenReturn(true);
            when(folder.getMessageCount()).thenReturn(0); when(store.isConnected()).thenReturn(true);
        }
        Object handle() throws Exception {
            var type=java.util.Arrays.stream(ImapIdleWatchService.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("IdleWatchHandle")).findFirst().orElseThrow();
            var constructor=type.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            var handle=constructor.newInstance(service,account,"INBOX",Duration.ofMinutes(25));
            doAnswer(call->{active(handle).set(false);return null;}).when(folder).idle();
            return handle;
        }
    }
    private static void awaitCancellation() throws MessagingException {
        try { new java.util.concurrent.CountDownLatch(1).await(); }
        catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Synthetic transport cancelled",interrupted);
        }
    }
    @SuppressWarnings("unchecked") private static Map<String,Boolean> pending(Object service) throws Exception {return (Map<String,Boolean>)get(service,"pendingSources");}
    private static AtomicBoolean active(Object handle) throws Exception {return (AtomicBoolean)get(handle,"active");}
    private static Object get(Object object,String name) throws Exception {var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void set(Object object,String name,Object value) throws Exception {var field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private static Object invoke(Object object,String name,Class<?>[] types,Object...args) throws Exception {
        var method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);
        try{return method.invoke(object,args);}catch(InvocationTargetException error){if(error.getCause() instanceof Exception cause)throw cause;throw error;}
    }
}
