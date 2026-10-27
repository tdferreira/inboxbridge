package dev.inboxbridge.service.polling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.AdminPollingSettingsView;
import dev.inboxbridge.dto.UpdateAdminPollingSettingsRequest;
import dev.inboxbridge.persistence.SystemPollingSetting;
import dev.inboxbridge.persistence.SystemPollingSettingRepository;
import jakarta.transaction.Transactional;

class PollingSettingsServiceTest {

    @Test
    void environmentAndAllMailboxInheritanceCombinationsAreValidatedBeforeAdminUpdate() {
        var service=service(new TestConfig(),new InMemorySystemPollingSettingRepository(null));
        var config=org.mockito.Mockito.mock(dev.inboxbridge.config.MailClientConfig.class);
        org.mockito.Mockito.when(config.idleRenewalInterval()).thenReturn(Duration.ofMinutes(10));
        org.mockito.Mockito.when(config.idleRenewalTimeout()).thenReturn(Duration.ofSeconds(20));
        org.mockito.Mockito.when(config.idleSafetyPollInterval()).thenReturn(Duration.ofMinutes(15));
        service.mailClientConfig=config;
        var sources=org.mockito.Mockito.mock(dev.inboxbridge.persistence.SourcePollingSettingRepository.class);
        service.sourceSettingsRepository=sources;
        @SuppressWarnings("unchecked") jakarta.enterprise.event.Event<dev.inboxbridge.service.mail.SourceMailboxConfigurationChanged> event=org.mockito.Mockito.mock(jakarta.enterprise.event.Event.class);
        service.mailboxChanged=event;
        for(String renewal:new String[]{null,"PT5M"}) for(String safety:new String[]{null,"PT15M"}) {
            var setting=new dev.inboxbridge.persistence.SourcePollingSetting();
            setting.idleRenewalIntervalOverride=renewal;setting.idleSafetyPollIntervalOverride=safety;
            org.mockito.Mockito.when(sources.listAll()).thenReturn(java.util.List.of(setting));
            service.update(idleRequest(null,null,null));
            service.update(new UpdateAdminPollingSettingsRequest(null,null,null,null,null,null,null,null,null,null,null,null,null,
                    true,null,null,"PT15M"));
        }
        assertEquals(Duration.ofMinutes(10),service.effectiveIdleSettings().renewalInterval());
        assertEquals(Duration.ofMinutes(15),service.effectiveIdleSafetyPollInterval());
        service.update(idleRequest(null,null,null));
        assertEquals(false,service.effectiveIdleSettings().safetyPollingEnabled());
        org.mockito.Mockito.verify(event,org.mockito.Mockito.times(9)).fire(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.when(config.idleSafetyPollingEnabled()).thenReturn(true);
        service.update(idleRequest(null,null,null));
        assertEquals(true,service.effectiveIdleSettings().safetyPollingEnabled());
    }

    private UpdateAdminPollingSettingsRequest idleRequest(Boolean enabled, String interval, String timeout) {
        return new UpdateAdminPollingSettingsRequest(null, null, null, null, null, null, null, null, null, null, null, null, null,
                enabled, interval, timeout);
    }

    @Test
    void rejectsExtremeSafetyAndSubMinuteRenewalBeforePersistence() {
        var repository = new InMemorySystemPollingSettingRepository(null);
        var service = service(new TestConfig(), repository);
        assertThrows(IllegalArgumentException.class, () -> service.update(new UpdateAdminPollingSettingsRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                true, null, null, "PT9223372036854775807S")));
        assertThrows(IllegalArgumentException.class, () -> service.update(idleRequest(true, "PT0.000000001S", "PT20S")));
        assertNull(repository.setting);
    }

    @Test
    void safetyDeadlineMustExceedTheCompleteRenewalWindow() {
        var repository = new InMemorySystemPollingSettingRepository(null);
        var service = service(new TestConfig(), repository);
        assertThrows(IllegalArgumentException.class, () -> service.update(new UpdateAdminPollingSettingsRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, true, "10m", "20s", "10m")));
        var view = service.update(new UpdateAdminPollingSettingsRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null, true, "10m", "20s", "15m"));
        assertEquals("PT15M", view.idleSettings().effectiveSafetyPollInterval());
        assertEquals(Duration.ofMinutes(15), service.effectiveIdleSafetyPollInterval());
        service.update(idleRequest(null, null, null));
        assertEquals(Duration.ofMinutes(30), service.effectiveIdleSafetyPollInterval());
    }

    @Test
    void adminCannotInvalidateAnExistingSourceSafetyOverride() {
        var service = service(new TestConfig(), new InMemorySystemPollingSettingRepository(null));
        service.sourceSettingsRepository = new dev.inboxbridge.persistence.SourcePollingSettingRepository() {
            @Override public java.util.List<dev.inboxbridge.persistence.SourcePollingSetting> listAll() {
                var setting = new dev.inboxbridge.persistence.SourcePollingSetting();
                setting.idleSafetyPollIntervalOverride = "PT15M";
                return java.util.List.of(setting);
            }
        };
        assertThrows(IllegalArgumentException.class, () -> service.update(idleRequest(true, "20m", "20s")));
    }

    @Test
    void idleOverridesPersistAndClearBackToEnvironmentDefaults() {
        var repository = new InMemorySystemPollingSettingRepository(null);
        var service = service(new TestConfig(), repository);
        var view = service.update(idleRequest(false, "20m", "15s")).idleSettings();
        assertEquals(false, view.effectiveSafetyPollingEnabled());
        assertEquals("PT20M", view.effectiveRenewalInterval());
        assertEquals("PT15S", view.effectiveRenewalTimeout());
        assertEquals(false, service.effectiveIdleSettings().safetyPollingEnabled());
        service.update(idleRequest(null, "", null));
        assertEquals(true, service.effectiveIdleSettings().safetyPollingEnabled());
        assertEquals(Duration.ofMinutes(25), service.effectiveIdleSettings().renewalInterval());
        assertNull(repository.setting.idleSafetyPollingEnabledOverride);
        assertNull(repository.setting.idleRenewalIntervalOverride);
    }

    @Test
    void invalidRenewalCombinationIsRejectedBeforeMutatingStoredSettings() {
        var repository = new InMemorySystemPollingSettingRepository(stored(true, "5m", 50, 5, 60));
        var service = service(new TestConfig(), repository);
        assertThrows(IllegalArgumentException.class, () -> service.update(idleRequest(false, "28m", "1m")));
        assertNull(repository.setting.idleSafetyPollingEnabledOverride);
        assertThrows(IllegalArgumentException.class, () -> service.update(idleRequest(true, "0s", "20s")));
        assertThrows(IllegalArgumentException.class, () -> service.update(idleRequest(true, "bad", "20s")));
        assertThrows(IllegalArgumentException.class, () -> service.update(idleRequest(true, "25m", "-1s")));
    }

    @Test
    void effectiveSettingsUseOverridesWhenPresent() {
        PollingSettingsService service = service(new TestConfig(), new InMemorySystemPollingSettingRepository(stored(Boolean.FALSE, "90s", 12, 7, 120)));

        PollingSettingsService.EffectivePollingSettings effective = service.effectiveSettings();
        PollingSettingsService.ManualPollRateLimit manualRateLimit = service.effectiveManualPollRateLimit();

        assertEquals(false, effective.pollEnabled());
        assertEquals("90s", effective.pollIntervalText());
        assertEquals(Duration.ofSeconds(90), effective.pollInterval());
        assertEquals(12, effective.fetchWindow());
        assertEquals(7, manualRateLimit.maxRuns());
        assertEquals(Duration.ofSeconds(120), manualRateLimit.window());
    }

    @Test
    void effectiveThrottleSettingsUseOverridesWhenPresent() {
        SystemPollingSetting setting = stored(Boolean.FALSE, "90s", 12, 7, 120);
        setting.sourceHostMinSpacingOverride = "PT2S";
        setting.sourceHostMaxConcurrencyOverride = 4;
        setting.destinationProviderMinSpacingOverride = "PT0.5S";
        setting.destinationProviderMaxConcurrencyOverride = 2;
        setting.throttleLeaseTtlOverride = "PT3M";
        setting.adaptiveThrottleMaxMultiplierOverride = 9;
        setting.successJitterRatioOverride = 0.35d;
        setting.maxSuccessJitterOverride = "PT45S";
        PollingSettingsService service = service(new TestConfig(), new InMemorySystemPollingSettingRepository(setting));

        PollingSettingsService.EffectiveThrottleSettings effective = service.effectiveThrottleSettings();

        assertEquals(Duration.ofSeconds(2), effective.sourceHostMinSpacing());
        assertEquals(4, effective.sourceHostMaxConcurrency());
        assertEquals(Duration.ofMillis(500), effective.destinationProviderMinSpacing());
        assertEquals(2, effective.destinationProviderMaxConcurrency());
        assertEquals(Duration.ofMinutes(3), effective.throttleLeaseTtl());
        assertEquals(9, effective.adaptiveThrottleMaxMultiplier());
        assertEquals(0.35d, effective.successJitterRatio());
        assertEquals(Duration.ofSeconds(45), effective.maxSuccessJitter());
    }

    @Test
    void updateClearsOverridesWhenRequestUsesNulls() {
        InMemorySystemPollingSettingRepository repository = new InMemorySystemPollingSettingRepository(stored(Boolean.TRUE, "3m", 15, 9, 180));
        PollingSettingsService service = service(new TestConfig(), repository);

        AdminPollingSettingsView view = service.update(new UpdateAdminPollingSettingsRequest(null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertNull(repository.setting.pollEnabledOverride);
        assertNull(repository.setting.pollIntervalOverride);
        assertNull(repository.setting.fetchWindowOverride);
        assertNull(repository.setting.manualTriggerLimitCountOverride);
        assertNull(repository.setting.manualTriggerLimitWindowSecondsOverride);
        assertNull(repository.setting.sourceHostMinSpacingOverride);
        assertNull(repository.setting.sourceHostMaxConcurrencyOverride);
        assertNull(repository.setting.destinationProviderMinSpacingOverride);
        assertNull(repository.setting.destinationProviderMaxConcurrencyOverride);
        assertNull(repository.setting.throttleLeaseTtlOverride);
        assertNull(repository.setting.adaptiveThrottleMaxMultiplierOverride);
        assertNull(repository.setting.successJitterRatioOverride);
        assertNull(repository.setting.maxSuccessJitterOverride);
        assertEquals("5m", view.effectivePollInterval());
        assertEquals(50, view.effectiveFetchWindow());
        assertEquals(PollingSettingsService.DEFAULT_MANUAL_TRIGGER_LIMIT_COUNT, view.effectiveManualTriggerLimitCount());
        assertEquals(PollingSettingsService.DEFAULT_MANUAL_TRIGGER_LIMIT_WINDOW_SECONDS, view.effectiveManualTriggerLimitWindowSeconds());
        assertEquals("PT1S", view.effectiveSourceHostMinSpacing());
        assertEquals(2, view.effectiveSourceHostMaxConcurrency());
    }

    @Test
    void rejectsTooSmallPollInterval() {
        PollingSettingsService service = service(new TestConfig(), new InMemorySystemPollingSettingRepository(null));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(new UpdateAdminPollingSettingsRequest(Boolean.TRUE, "4s", Integer.valueOf(10), null, null, null, null, null, null, null, null, null, null)));

        assertEquals("Poll interval must be at least 5 seconds", error.getMessage());
    }

    @Test
    void rejectsInvalidManualPollWindow() {
        PollingSettingsService service = service(new TestConfig(), new InMemorySystemPollingSettingRepository(null));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(new UpdateAdminPollingSettingsRequest(Boolean.TRUE, "5m", Integer.valueOf(10), Integer.valueOf(5), Integer.valueOf(5), null, null, null, null, null, null, null, null)));

        assertEquals("Manual poll rate-limit window must be between 10 and 3600 seconds", error.getMessage());
    }

    @Test
    void rejectsInvalidSuccessJitterRatio() {
        PollingSettingsService service = service(new TestConfig(), new InMemorySystemPollingSettingRepository(null));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(new UpdateAdminPollingSettingsRequest(
                        Boolean.TRUE,
                        "5m",
                        Integer.valueOf(10),
                        Integer.valueOf(5),
                        Integer.valueOf(60),
                        "PT1S",
                        Integer.valueOf(2),
                        "PT0.25S",
                        Integer.valueOf(1),
                        "PT2M",
                        Integer.valueOf(6),
                        Double.valueOf(1.5d),
                        "PT30S")));

        assertEquals("Success jitter ratio must be between 0 and 1", error.getMessage());
    }

    @Test
    void repositoryBackedReadMethodsRemainTransactional() throws NoSuchMethodException {
        assertEquals(true, PollingSettingsService.class.getMethod("effectiveSettings").isAnnotationPresent(Transactional.class));
        assertEquals(true, PollingSettingsService.class.getMethod("effectiveManualPollRateLimit").isAnnotationPresent(Transactional.class));
        assertEquals(true, PollingSettingsService.class.getMethod("effectiveThrottleSettings").isAnnotationPresent(Transactional.class));
    }

    private PollingSettingsService service(InboxBridgeConfig config, SystemPollingSettingRepository repository) {
        PollingSettingsService service = new PollingSettingsService();
        service.inboxBridgeConfig = config;
        service.repository = repository;
        return service;
    }

    private SystemPollingSetting stored(Boolean enabled, String interval, Integer fetchWindow, Integer manualLimitCount, Integer manualWindowSeconds) {
        SystemPollingSetting setting = new SystemPollingSetting();
        setting.id = SystemPollingSetting.SINGLETON_ID;
        setting.pollEnabledOverride = enabled;
        setting.pollIntervalOverride = interval;
        setting.fetchWindowOverride = fetchWindow;
        setting.manualTriggerLimitCountOverride = manualLimitCount;
        setting.manualTriggerLimitWindowSecondsOverride = manualWindowSeconds;
        setting.updatedAt = java.time.Instant.now();
        return setting;
    }

    private static final class InMemorySystemPollingSettingRepository extends SystemPollingSettingRepository {
        private SystemPollingSetting setting;

        private InMemorySystemPollingSettingRepository(SystemPollingSetting setting) {
            this.setting = setting;
        }

        @Override
        public Optional<SystemPollingSetting> findSingleton() {
            return Optional.ofNullable(setting);
        }

        @Override
        public void persist(SystemPollingSetting entity) {
            setting = entity;
        }
    }

    private static final class TestConfig implements InboxBridgeConfig {
        @Override
        public boolean pollEnabled() {
            return true;
        }

        @Override
        public String pollInterval() {
            return "5m";
        }

        @Override
        public int fetchWindow() {
            return 50;
        }

        @Override
        public Duration sourceHostMinSpacing() {
            return Duration.ofSeconds(1);
        }

        @Override
        public int sourceHostMaxConcurrency() {
            return 2;
        }

        @Override
        public Duration destinationProviderMinSpacing() {
            return Duration.ofMillis(250);
        }

        @Override
        public int destinationProviderMaxConcurrency() {
            return 1;
        }

        @Override
        public Duration throttleLeaseTtl() {
            return Duration.ofMinutes(2);
        }

        @Override
        public int adaptiveThrottleMaxMultiplier() {
            return 6;
        }

        @Override
        public double successJitterRatio() {
            return 0.2d;
        }

        @Override
        public Duration maxSuccessJitter() {
            return Duration.ofSeconds(30);
        }

        @Override
        public boolean multiUserEnabled() {
            return true;
        }

        @Override
        public Security security() {
            return null;
        }

        @Override
        public Gmail gmail() {
            return null;
        }

        @Override
        public Microsoft microsoft() {
            return null;
        }

        @Override
        public java.util.List<Source> sources() {
            return java.util.List.of();
        }
    }
}
