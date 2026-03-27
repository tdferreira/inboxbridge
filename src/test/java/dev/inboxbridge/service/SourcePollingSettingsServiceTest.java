package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.domain.RuntimeBridge;
import dev.inboxbridge.dto.UpdateSourcePollingSettingsRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.SourcePollingSetting;
import dev.inboxbridge.persistence.SourcePollingSettingRepository;

class SourcePollingSettingsServiceTest {

    @Test
    void effectiveSettingsFallBackToInheritedUserPolling() {
        SourcePollingSettingsService service = service();

        var effective = service.effectiveSettingsFor(runtimeBridge());

        assertEquals(false, effective.pollEnabled());
        assertEquals("7m", effective.pollIntervalText());
        assertEquals(12, effective.fetchWindow());
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

    private SourcePollingSettingsService service() {
        SourcePollingSettingsService service = new SourcePollingSettingsService();
        service.repository = new InMemorySourcePollingSettingRepository();
        service.runtimeBridgeService = new RuntimeBridgeService() {
            @Override
            public Optional<RuntimeBridge> findAccessibleForUser(AppUser actor, String sourceId) {
                return "fetcher-1".equals(sourceId) ? Optional.of(runtimeBridge()) : Optional.empty();
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

    private RuntimeBridge runtimeBridge() {
        return new RuntimeBridge(
                "fetcher-1",
                "USER",
                7L,
                "alice",
                true,
                BridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                BridgeConfig.AuthMethod.PASSWORD,
                BridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "",
                Optional.of("INBOX"),
                false,
                Optional.of("Imported/Test"),
                null);
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
