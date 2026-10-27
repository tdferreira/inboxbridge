package dev.inboxbridge.service.polling;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.persistence.SourcePollingSetting;
import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.service.mail.ImapIdleHealthService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import dev.inboxbridge.testsupport.PostgresQuarkusTestProfile;
import dev.inboxbridge.testsupport.PostgresTestResource;
import io.quarkus.arc.ClientProxy;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;

@QuarkusTest
@TestProfile(PostgresQuarkusTestProfile.class)
@QuarkusTestResource(value = PostgresTestResource.class, restrictToAnnotatedClass = true)
class PostgresIdleSchedulerQuarkusTest {
    @Inject PollingService polling;
    @Inject ImapIdleHealthService health;
    @Inject EntityManager entities;
    @Inject TransactionManager transactions;

    @Test
    void parallelWorkersReadDetachedStateAndReleaseTransactionsBeforeMailboxIo() throws Exception {
        exercise(false);
    }

    @Test
    void corruptMailboxSafetySettingDoesNotStopOtherUsersSources() throws Exception {
        exercise(true);
    }

    private void exercise(boolean includeInvalid) throws Exception {
        String prefix = "idle-pg-" + UUID.randomUUID();
        var first = account(prefix + "-alice", 7L);
        var second = account(prefix + "-bob", 8L);
        var invalid = account(prefix + "-invalid", 9L);
        List<RuntimeEmailAccount> accounts = includeInvalid ? List.of(invalid, first, second) : List.of(first, second);
        QuarkusTransaction.requiringNew().run(() -> {
            for (var account : accounts) {
                var setting = new SourcePollingSetting();
                setting.sourceId = account.id(); setting.pollEnabledOverride = true;
                setting.idleSafetyPollIntervalOverride = account == invalid ? "PT9223372036854775807S" : "PT30M";
                setting.updatedAt = Instant.now(); entities.persist(setting);
                var state = new SourcePollingState(); state.sourceId = account.id();
                state.updatedAt = Instant.now(); state.lastSuccessAt = Instant.now().minusSeconds(7200);
                entities.persist(state);
            }
        });
        accounts.forEach(a -> health.markConnected(a.id(), Instant.now().minusSeconds(1900)));
        PollingService bean = ClientProxy.unwrap(polling);
        var originalRuntime = bean.runtimeEmailAccountService;
        var originalExecution = bean.pollingSourceExecutionService;
        var arrived = new CountDownLatch(2);
        var executed = new AtomicInteger();
        bean.runtimeEmailAccountService = new RuntimeEmailAccountService() {
            @Override public List<RuntimeEmailAccount> listEnabledForPolling() { return accounts; }
        };
        // Keep real CDI/settings/state repositories; substitute only external mailbox I/O.
        bean.pollingSourceExecutionService = new PollingSourceExecutionService(null, null, null, null, null, null, null, null) {
            @Override SourceExecutionOutcome execute(RuntimeEmailAccount account, String trigger,
                    PollingSettingsService.EffectivePollingSettings settings, String liveRunId,
                    String actorUsername, String surface) {
                assertTrue(Thread.currentThread().isVirtual());
                try {
                    assertEquals(Status.STATUS_NO_TRANSACTION, transactions.getStatus());
                    arrived.countDown(); assertTrue(arrived.await(5, TimeUnit.SECONDS));
                } catch (Exception error) { throw new IllegalStateException(error); }
                assertNotEquals(invalid.id(), account.id());
                executed.incrementAndGet();
                return new SourceExecutionOutcome(1, 1, 0, 0, 0, List.of(), null);
            }
        };
        try {
            var result = polling.runPoll("scheduler");
            assertTrue(result.getErrors().isEmpty(), result.getErrors().toString());
            assertEquals(2, executed.get()); assertEquals(2, result.getImported());
        } finally {
            bean.runtimeEmailAccountService = originalRuntime; bean.pollingSourceExecutionService = originalExecution;
            accounts.forEach(a -> health.clear(a.id()));
            QuarkusTransaction.requiringNew().run(() -> {
                for (var a : accounts) {
                    entities.createQuery("delete from SourcePollingSetting where sourceId = :id").setParameter("id", a.id()).executeUpdate();
                    entities.createQuery("delete from SourcePollingState where sourceId = :id").setParameter("id", a.id()).executeUpdate();
                }
            });
        }
    }

    private RuntimeEmailAccount account(String id, Long owner) {
        return new RuntimeEmailAccount(id, "USER", owner, "synthetic-user", true,
                InboxBridgeConfig.Protocol.IMAP, "unused.example.test", 993, true,
                InboxBridgeConfig.AuthMethod.PASSWORD, InboxBridgeConfig.OAuthProvider.NONE,
                "source@example.test", "synthetic", "", Optional.of("INBOX"), false,
                SourceFetchMode.IDLE, Optional.empty(), null);
    }
}
