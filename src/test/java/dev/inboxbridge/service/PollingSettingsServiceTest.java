package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.dto.AdminPollingSettingsView;
import dev.inboxbridge.dto.UpdateAdminPollingSettingsRequest;
import dev.inboxbridge.persistence.SystemPollingSetting;
import dev.inboxbridge.persistence.SystemPollingSettingRepository;

class PollingSettingsServiceTest {

    @Test
    void effectiveSettingsUseOverridesWhenPresent() {
        PollingSettingsService service = service(new TestConfig(), new InMemorySystemPollingSettingRepository(stored(Boolean.FALSE, "90s", 12)));

        PollingSettingsService.EffectivePollingSettings effective = service.effectiveSettings();

        assertEquals(false, effective.pollEnabled());
        assertEquals("90s", effective.pollIntervalText());
        assertEquals(Duration.ofSeconds(90), effective.pollInterval());
        assertEquals(12, effective.fetchWindow());
    }

    @Test
    void updateClearsOverridesWhenRequestUsesNulls() {
        InMemorySystemPollingSettingRepository repository = new InMemorySystemPollingSettingRepository(stored(Boolean.TRUE, "3m", 15));
        PollingSettingsService service = service(new TestConfig(), repository);

        AdminPollingSettingsView view = service.update(new UpdateAdminPollingSettingsRequest(null, null, null));

        assertNull(repository.setting.pollEnabledOverride);
        assertNull(repository.setting.pollIntervalOverride);
        assertNull(repository.setting.fetchWindowOverride);
        assertEquals("5m", view.effectivePollInterval());
        assertEquals(50, view.effectiveFetchWindow());
    }

    @Test
    void rejectsTooSmallPollInterval() {
        PollingSettingsService service = service(new TestConfig(), new InMemorySystemPollingSettingRepository(null));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(new UpdateAdminPollingSettingsRequest(Boolean.TRUE, "4s", Integer.valueOf(10))));

        assertEquals("Poll interval must be at least 5 seconds", error.getMessage());
    }

    private PollingSettingsService service(BridgeConfig config, SystemPollingSettingRepository repository) {
        PollingSettingsService service = new PollingSettingsService();
        service.bridgeConfig = config;
        service.repository = repository;
        return service;
    }

    private SystemPollingSetting stored(Boolean enabled, String interval, Integer fetchWindow) {
        SystemPollingSetting setting = new SystemPollingSetting();
        setting.id = SystemPollingSetting.SINGLETON_ID;
        setting.pollEnabledOverride = enabled;
        setting.pollIntervalOverride = interval;
        setting.fetchWindowOverride = fetchWindow;
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

    private static final class TestConfig implements BridgeConfig {
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
