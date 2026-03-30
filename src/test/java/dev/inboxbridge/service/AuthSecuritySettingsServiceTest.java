package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.AuthSecuritySettingsView;
import dev.inboxbridge.dto.UpdateAuthSecuritySettingsRequest;
import dev.inboxbridge.persistence.SystemAuthSecuritySetting;
import dev.inboxbridge.persistence.SystemAuthSecuritySettingRepository;

class AuthSecuritySettingsServiceTest {

    @Test
    void effectiveSettingsUseOverridesWhenPresent() {
        SystemAuthSecuritySetting setting = new SystemAuthSecuritySetting();
        setting.id = SystemAuthSecuritySetting.SINGLETON_ID;
        setting.loginFailureThresholdOverride = 8;
        setting.loginInitialBlockOverride = "PT10M";
        setting.loginMaxBlockOverride = "PT2H";
        setting.registrationChallengeEnabledOverride = Boolean.FALSE;
        setting.registrationChallengeTtlOverride = "PT20M";
        AuthSecuritySettingsService service = service(new TestConfig(), new InMemorySystemAuthSecuritySettingRepository(setting));

        AuthSecuritySettingsService.EffectiveAuthSecuritySettings effective = service.effectiveSettings();

        assertEquals(8, effective.loginFailureThreshold());
        assertEquals(Duration.ofMinutes(10), effective.loginInitialBlock());
        assertEquals(Duration.ofHours(2), effective.loginMaxBlock());
        assertEquals(false, effective.registrationChallengeEnabled());
        assertEquals(Duration.ofMinutes(20), effective.registrationChallengeTtl());
    }

    @Test
    void updateClearsOverridesWhenRequestUsesNulls() {
        SystemAuthSecuritySetting setting = new SystemAuthSecuritySetting();
        setting.id = SystemAuthSecuritySetting.SINGLETON_ID;
        setting.loginFailureThresholdOverride = 8;
        setting.loginInitialBlockOverride = "PT10M";
        setting.loginMaxBlockOverride = "PT2H";
        setting.registrationChallengeEnabledOverride = Boolean.FALSE;
        setting.registrationChallengeTtlOverride = "PT20M";
        InMemorySystemAuthSecuritySettingRepository repository = new InMemorySystemAuthSecuritySettingRepository(setting);
        AuthSecuritySettingsService service = service(new TestConfig(), repository);

        AuthSecuritySettingsView view = service.update(new UpdateAuthSecuritySettingsRequest(null, null, null, null, null));

        assertNull(repository.setting.loginFailureThresholdOverride);
        assertNull(repository.setting.loginInitialBlockOverride);
        assertNull(repository.setting.loginMaxBlockOverride);
        assertNull(repository.setting.registrationChallengeEnabledOverride);
        assertNull(repository.setting.registrationChallengeTtlOverride);
        assertEquals(5, view.effectiveLoginFailureThreshold());
        assertEquals("PT5M", view.effectiveLoginInitialBlock());
    }

    @Test
    void rejectsWhenMaximumBlockIsShorterThanInitialBlock() {
        AuthSecuritySettingsService service = service(new TestConfig(), new InMemorySystemAuthSecuritySettingRepository(null));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(new UpdateAuthSecuritySettingsRequest(5, "PT30M", "PT10M", Boolean.TRUE, "PT10M")));

        assertEquals("Maximum login block must be greater than or equal to the initial login block", error.getMessage());
    }

    private AuthSecuritySettingsService service(InboxBridgeConfig config, SystemAuthSecuritySettingRepository repository) {
        AuthSecuritySettingsService service = new AuthSecuritySettingsService();
        service.inboxBridgeConfig = config;
        service.repository = repository;
        return service;
    }

    private static final class InMemorySystemAuthSecuritySettingRepository extends SystemAuthSecuritySettingRepository {
        private SystemAuthSecuritySetting setting;

        private InMemorySystemAuthSecuritySettingRepository(SystemAuthSecuritySetting setting) {
            this.setting = setting;
        }

        @Override
        public Optional<SystemAuthSecuritySetting> findSingleton() {
            return Optional.ofNullable(setting);
        }

        @Override
        public void persist(SystemAuthSecuritySetting entity) {
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
            return new Security() {
                @Override
                public Auth auth() {
                    return new Auth() {
                        @Override
                        public int loginFailureThreshold() {
                            return 5;
                        }

                        @Override
                        public Duration loginInitialBlock() {
                            return Duration.ofMinutes(5);
                        }

                        @Override
                        public Duration loginMaxBlock() {
                            return Duration.ofHours(1);
                        }

                        @Override
                        public boolean registrationChallengeEnabled() {
                            return true;
                        }

                        @Override
                        public Duration registrationChallengeTtl() {
                            return Duration.ofMinutes(10);
                        }
                    };
                }

                @Override
                public Passkeys passkeys() {
                    return new Passkeys() {
                        @Override
                        public boolean enabled() {
                            return true;
                        }

                        @Override
                        public String rpId() {
                            return "localhost";
                        }

                        @Override
                        public String rpName() {
                            return "InboxBridge";
                        }

                        @Override
                        public String origins() {
                            return "https://localhost:3000";
                        }

                        @Override
                        public String challengeTtl() {
                            return "PT5M";
                        }
                    };
                }
            };
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
