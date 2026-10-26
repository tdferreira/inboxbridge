package dev.inboxbridge.service.polling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.SourcePollingSetting;
import dev.inboxbridge.persistence.SourcePollingSettingRepository;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import dev.inboxbridge.service.user.UserPollingSettingsService;
import jakarta.transaction.Transactional;

class SourcePollingSettingsServiceTest {

    @Test
    void absentMailboxOverridesAppearAsInheritedAndUpdatesPublishConfigurationChanges() {
        var service=service();
        var initial=service.viewForSource(actor(),"fetcher-1").orElseThrow();
        assertEquals(null,initial.idleRenewalIntervalOverride());
        assertEquals(null,initial.idleSafetyPollIntervalOverride());
        assertEquals(null,initial.idleSafetyPollingEnabledOverride());
        @SuppressWarnings("unchecked") jakarta.enterprise.event.Event<dev.inboxbridge.service.mail.SourceMailboxConfigurationChanged> event=org.mockito.Mockito.mock(jakarta.enterprise.event.Event.class);
        service.mailboxChanged=event;
        service.updateForSource(actor(),"fetcher-1",new UpdateSourcePollingSettingsRequest(null,null,null,true,"  "," "));
        org.mockito.Mockito.verify(event).fire(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void effectiveIdlePolicyResolvesMissingServicesAndEachNullableOverride() {
        var defaults=new SourcePollingSettingsService();
        var account=runtimeBridge();
        assertEquals(true,defaults.effectiveIdleSafetyPollingEnabled(account));
        assertEquals(java.time.Duration.ofMinutes(30),defaults.effectiveIdleSafetyPollInterval(account));
        assertEquals(java.time.Duration.ofMinutes(25),defaults.effectiveIdleRenewalInterval(account));
        var repo=org.mockito.Mockito.mock(dev.inboxbridge.persistence.SourcePollingSettingRepository.class);
        defaults.repository=repo;
        var setting=new dev.inboxbridge.persistence.SourcePollingSetting();
        org.mockito.Mockito.when(repo.findBySourceId(account.id())).thenReturn(java.util.Optional.of(setting));
        assertEquals(true,defaults.effectiveIdleSafetyPollingEnabled(account));
        assertEquals(java.time.Duration.ofMinutes(30),defaults.effectiveIdleSafetyPollInterval(account));
        assertEquals(java.time.Duration.ofMinutes(25),defaults.effectiveIdleRenewalInterval(account));
        defaults.pollingSettingsService=org.mockito.Mockito.mock(PollingSettingsService.class);
        org.mockito.Mockito.when(defaults.pollingSettingsService.effectiveIdleSettings()).thenReturn(
            new PollingSettingsService.EffectiveIdleSettings(false,java.time.Duration.ofMinutes(10),java.time.Duration.ofSeconds(20)));
        org.mockito.Mockito.when(defaults.pollingSettingsService.effectiveIdleSafetyPollInterval()).thenReturn(java.time.Duration.ofMinutes(15));
        assertEquals(false,defaults.effectiveIdleSafetyPollingEnabled(account));
        assertEquals(java.time.Duration.ofMinutes(10),defaults.effectiveIdleRenewalInterval(account));
        assertEquals(java.time.Duration.ofMinutes(15),defaults.effectiveIdleSafetyPollInterval(account));
        setting.idleSafetyPollingEnabledOverride=true;setting.idleSafetyPollIntervalOverride="PT20M";setting.idleRenewalIntervalOverride="PT5M";
        assertEquals(true,defaults.effectiveIdleSafetyPollingEnabled(account));
        assertEquals(java.time.Duration.ofMinutes(20),defaults.effectiveIdleSafetyPollInterval(account));
        assertEquals(java.time.Duration.ofMinutes(5),defaults.effectiveIdleRenewalInterval(account));
    }

    @Test
    void rejectsExtremeSafetyAndSubMinuteRenewalForMailboxOwner() {
        var service = service();
        assertThrows(IllegalArgumentException.class, () -> service.updateForSource(actor(), "fetcher-1",
                new UpdateSourcePollingSettingsRequest(null, null, null, true, "PT9223372036854775807S", null)));
        assertThrows(IllegalArgumentException.class, () -> service.updateForSource(actor(), "fetcher-1",
                new UpdateSourcePollingSettingsRequest(null, null, null, true, null, "PT0.000000001S")));
    }

    @Test
    void mailboxRenewalAndSafetyDeadlineAreValidatedTogether() {
        var service = service();
        var view = service.updateForSource(actor(), "fetcher-1", new UpdateSourcePollingSettingsRequest(null, null, null, true, "15m", "10m"));
        assertEquals("PT10M", view.effectiveIdleRenewalInterval());
        assertEquals("PT15M", view.effectiveIdleSafetyPollInterval());
        assertThrows(IllegalArgumentException.class, () -> service.updateForSource(actor(), "fetcher-1",
                new UpdateSourcePollingSettingsRequest(null, null, null, true, "15m", "16m")));
        assertThrows(IllegalArgumentException.class, () -> service.updateForSource(actor(), "fetcher-1",
                new UpdateSourcePollingSettingsRequest(null, null, null, true, "40m", "29m")));
        assertEquals(java.time.Duration.ofMinutes(10), service.effectiveIdleRenewalInterval(runtimeBridge()));
        var cleared = service.updateForSource(actor(), "fetcher-1", new UpdateSourcePollingSettingsRequest(null, null, null, null, null, null));
        assertEquals("PT25M", cleared.effectiveIdleRenewalInterval());
    }

    @Test
    void sourceSafetyDeadlineInheritsValidatesAndClears() {
        var service = service();
        assertThrows(IllegalArgumentException.class, () -> service.updateForSource(actor(), "fetcher-1",
                new UpdateSourcePollingSettingsRequest(null, null, null, true, "20m")));
        var view = service.updateForSource(actor(), "fetcher-1", new UpdateSourcePollingSettingsRequest(null, null, null, true, "40m"));
        assertEquals("PT40M", view.effectiveIdleSafetyPollInterval());
        assertEquals("PT30M", view.baseIdleSafetyPollInterval());
        var cleared = service.updateForSource(actor(), "fetcher-1", new UpdateSourcePollingSettingsRequest(null, null, null, null, ""));
        assertEquals("PT30M", cleared.effectiveIdleSafetyPollInterval());
        assertEquals(null, cleared.idleSafetyPollIntervalOverride());
    }

    @Test
    void sourceSafetyPolicyOverridesAndReturnsToGlobalDefaults() {
        var service = service();
        var disabled = service.updateForSource(actor(), "fetcher-1", new UpdateSourcePollingSettingsRequest(true, "2m", 33, false));
        assertEquals(false, disabled.effectiveIdleSafetyPollingEnabled());
        assertEquals(true, disabled.baseIdleSafetyPollingEnabled());
        assertEquals(false, service.effectiveIdleSafetyPollingEnabled(runtimeBridge()));
        var inherited = service.updateForSource(actor(), "fetcher-1", new UpdateSourcePollingSettingsRequest(null, null, null, null));
        assertEquals(true, inherited.effectiveIdleSafetyPollingEnabled());
        assertEquals(null, inherited.idleSafetyPollingEnabledOverride());
        service.pollingSettingsService = new PollingSettingsService() {
            @Override public EffectiveIdleSettings effectiveIdleSettings() {
                return new EffectiveIdleSettings(false, Duration.ofMinutes(25), Duration.ofSeconds(20));
            }
        };
        assertEquals(false, service.effectiveIdleSafetyPollingEnabled(runtimeBridge()));
        service.updateForSource(actor(), "fetcher-1", new UpdateSourcePollingSettingsRequest(null, null, null, true));
        assertEquals(true, service.effectiveIdleSafetyPollingEnabled(runtimeBridge()));
    }

    @Test
    void sourceSafetySettingCannotBeChangedForAnInaccessibleMailbox() {
        var service = service();
        assertThrows(IllegalArgumentException.class, () -> service.updateForSource(actor(), "someone-elses-source",
                new UpdateSourcePollingSettingsRequest(null, null, null, false)));
    }

    @Test
    void effectiveSettingsFallBackToInheritedUserPolling() {
        SourcePollingSettingsService service = service();

        var effective = service.effectiveSettingsFor(runtimeBridge());

        assertEquals(false, effective.pollEnabled());
        assertEquals("7m", effective.pollIntervalText());
        assertEquals(12, effective.fetchWindow());
    }

    @Test
    void effectiveSettingsForSystemSourceFallBackToGlobalPolling() {
        SourcePollingSettingsService service = service();

        var effective = service.effectiveSettingsFor(systemBridge());

        assertEquals(true, effective.pollEnabled());
        assertEquals("5m", effective.pollIntervalText());
        assertEquals(50, effective.fetchWindow());
    }

    @Test
    void sourceOverridesTakePrecedenceOverUserAndGlobalSettingsPerField() {
        SourcePollingSettingsService service = service();
        service.repository.persist(storedSetting("fetcher-1", Boolean.TRUE, null, Integer.valueOf(33)));

        var effective = service.effectiveSettingsFor(runtimeBridge());

        assertEquals(true, effective.pollEnabled());
        assertEquals("7m", effective.pollIntervalText());
        assertEquals(33, effective.fetchWindow());
    }

    @Test
    void updateStoresPerSourceOverrides() {
        SourcePollingSettingsService service = service();

        var view = service.updateForSource(actor(), "fetcher-1", new UpdateSourcePollingSettingsRequest(Boolean.TRUE, "2m", Integer.valueOf(33)));

        assertEquals(Boolean.TRUE, view.pollEnabledOverride());
        assertEquals(true, view.effectivePollEnabled());
        assertEquals("2m", view.effectivePollInterval());
        assertEquals(33, view.effectiveFetchWindow());
    }

    @Test
    void invalidFetchWindowIsRejected() {
        SourcePollingSettingsService service = service();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateForSource(actor(), "fetcher-1", new UpdateSourcePollingSettingsRequest(Boolean.TRUE, "2m", Integer.valueOf(0))));

        assertEquals("Fetch window override must be between 1 and 500 messages", error.getMessage());
    }

    @Test
    void effectiveSettingsReadMethodRemainsTransactional() throws NoSuchMethodException {
        assertEquals(
                true,
                SourcePollingSettingsService.class
                        .getMethod("effectiveSettingsFor", RuntimeEmailAccount.class)
                        .isAnnotationPresent(Transactional.class));
    }

    private SourcePollingSettingsService service() {
        SourcePollingSettingsService service = new SourcePollingSettingsService();
        service.repository = new InMemorySourcePollingSettingRepository();
        service.runtimeEmailAccountService = new RuntimeEmailAccountService() {
            @Override
            public Optional<RuntimeEmailAccount> findAccessibleForUser(AppUser actor, String sourceId) {
                return "fetcher-1".equals(sourceId) ? Optional.of(runtimeBridge()) : Optional.empty();
            }

            @Override
            public Optional<RuntimeEmailAccount> findSystemBridge(String sourceId) {
                return "system-fetcher".equals(sourceId) ? Optional.of(systemBridge()) : Optional.empty();
            }
        };
        service.pollingSettingsService = new PollingSettingsService() {
            @Override
            public EffectivePollingSettings effectiveSettings() {
                return new EffectivePollingSettings(true, "5m", Duration.ofMinutes(5), 50);
            }
        };
        service.userPollingSettingsService = new UserPollingSettingsService() {
            @Override
            public PollingSettingsService.EffectivePollingSettings effectiveSettingsForUser(Long userId) {
                return new PollingSettingsService.EffectivePollingSettings(false, "7m", Duration.ofMinutes(7), 12);
            }
        };
        return service;
    }

    private AppUser actor() {
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";
        return user;
    }

    private RuntimeEmailAccount runtimeBridge() {
        return new RuntimeEmailAccount(
                "fetcher-1",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                Optional.of("Imported/Test"),
                null);
    }

    private RuntimeEmailAccount systemBridge() {
        return new RuntimeEmailAccount(
                "system-fetcher",
                "SYSTEM",
                null,
                "system",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.system.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "system@example.com",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                Optional.empty(),
                null);
    }

    private SourcePollingSetting storedSetting(String sourceId, Boolean enabled, String interval, Integer fetchWindow) {
        SourcePollingSetting setting = new SourcePollingSetting();
        setting.sourceId = sourceId;
        setting.ownerUserId = 7L;
        setting.pollEnabledOverride = enabled;
        setting.pollIntervalOverride = interval;
        setting.fetchWindowOverride = fetchWindow;
        return setting;
    }

    private static final class InMemorySourcePollingSettingRepository extends SourcePollingSettingRepository {
        private SourcePollingSetting setting;

        @Override
        public Optional<SourcePollingSetting> findBySourceId(String sourceId) {
            return setting != null && sourceId.equals(setting.sourceId) ? Optional.of(setting) : Optional.empty();
        }

        @Override
        public void persist(SourcePollingSetting entity) {
            if (entity.id == null) {
                entity.id = 1L;
            }
            if (entity.updatedAt == null) {
                entity.updatedAt = Instant.now();
            }
            setting = entity;
        }
    }
}
