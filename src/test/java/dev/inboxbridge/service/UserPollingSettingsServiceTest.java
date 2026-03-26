package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.UpdateUserPollingSettingsRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.UserPollingSetting;
import dev.inboxbridge.persistence.UserPollingSettingRepository;

class UserPollingSettingsServiceTest {

    @Test
    void defaultViewFallsBackToGlobalPollingSettings() {
        UserPollingSettingsService service = service();

        var view = service.defaultView(7L);

        assertEquals(true, view.defaultPollEnabled());
        assertEquals("5m", view.effectivePollInterval());
        assertEquals(50, view.effectiveFetchWindow());
    }

    @Test
    void updateStoresUserOverridesAndChangesEffectiveSettings() {
        UserPollingSettingsService service = service();
        AppUser user = user(7L);

        var view = service.update(user, new UpdateUserPollingSettingsRequest(Boolean.FALSE, "2m", Integer.valueOf(20)));

        assertEquals(Boolean.FALSE, view.pollEnabledOverride());
        assertEquals(false, view.effectivePollEnabled());
        assertEquals("2m", view.effectivePollInterval());
        assertEquals(20, view.effectiveFetchWindow());
    }

    @Test
    void invalidFetchWindowIsRejected() {
        UserPollingSettingsService service = service();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(user(7L), new UpdateUserPollingSettingsRequest(Boolean.TRUE, "5m", Integer.valueOf(0))));

        assertEquals("Fetch window override must be between 1 and 500 messages", error.getMessage());
    }

    private UserPollingSettingsService service() {
        UserPollingSettingsService service = new UserPollingSettingsService();
        service.repository = new InMemoryUserPollingSettingRepository();
        service.pollingSettingsService = new FakePollingSettingsService();
        return service;
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        user.id = id;
        user.username = "user-" + id;
        return user;
    }

    private static final class FakePollingSettingsService extends PollingSettingsService {
        @Override
        public EffectivePollingSettings effectiveSettings() {
            return new EffectivePollingSettings(true, "5m", Duration.ofMinutes(5), 50);
        }
    }

    private static final class InMemoryUserPollingSettingRepository extends UserPollingSettingRepository {
        private UserPollingSetting setting;

        @Override
        public Optional<UserPollingSetting> findByUserId(Long userId) {
            return setting != null && userId.equals(setting.userId) ? Optional.of(setting) : Optional.empty();
        }

        @Override
        public void persist(UserPollingSetting entity) {
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
