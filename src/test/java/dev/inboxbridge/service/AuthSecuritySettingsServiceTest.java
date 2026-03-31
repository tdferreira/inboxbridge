package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Base64;
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
        setting.registrationChallengeProviderOverride = "HCAPTCHA";
        setting.registrationHcaptchaSiteKeyOverride = "site-key";
        setting.registrationHcaptchaSecretCiphertext = "cipher";
        setting.registrationHcaptchaSecretNonce = "nonce";
        setting.keyVersion = "test-key";
        setting.geoIpEnabledOverride = Boolean.TRUE;
        setting.geoIpPrimaryProviderOverride = "IPAPI_CO";
        setting.geoIpFallbackProvidersOverride = "IPWHOIS,IP_API";
        setting.geoIpCacheTtlOverride = "PT48H";
        setting.geoIpProviderCooldownOverride = "PT10M";
        setting.geoIpRequestTimeoutOverride = "PT5S";
        AuthSecuritySettingsService service = service(new TestConfig(), new InMemorySystemAuthSecuritySettingRepository(setting));
        service.secretEncryptionService = passthroughSecrets();

        AuthSecuritySettingsService.EffectiveAuthSecuritySettings effective = service.effectiveSettings();

        assertEquals(8, effective.loginFailureThreshold());
        assertEquals(Duration.ofMinutes(10), effective.loginInitialBlock());
        assertEquals(Duration.ofHours(2), effective.loginMaxBlock());
        assertEquals(false, effective.registrationChallengeEnabled());
        assertEquals(Duration.ofMinutes(20), effective.registrationChallengeTtl());
        assertEquals("HCAPTCHA", effective.registrationChallengeProvider());
        assertEquals("site-key", effective.registrationHcaptchaSiteKey());
        assertEquals("cipher", effective.registrationHcaptchaSecret());
        assertEquals(true, effective.geoIpEnabled());
        assertEquals("IPAPI_CO", effective.geoIpPrimaryProvider());
        assertEquals("IPWHOIS,IP_API", effective.geoIpFallbackProviders());
        assertEquals(Duration.ofHours(48), effective.geoIpCacheTtl());
        assertEquals(Duration.ofMinutes(10), effective.geoIpProviderCooldown());
        assertEquals(Duration.ofSeconds(5), effective.geoIpRequestTimeout());
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
        setting.registrationChallengeProviderOverride = "TURNSTILE";
        setting.registrationTurnstileSiteKeyOverride = "site-key";
        setting.geoIpEnabledOverride = Boolean.TRUE;
        setting.geoIpPrimaryProviderOverride = "IPAPI_CO";
        InMemorySystemAuthSecuritySettingRepository repository = new InMemorySystemAuthSecuritySettingRepository(setting);
        AuthSecuritySettingsService service = service(new TestConfig(), repository);

        AuthSecuritySettingsView view = service.update(new UpdateAuthSecuritySettingsRequest(
                null, null, null,
                null, null, null,
                null, null, null, null,
                null, null, null, null, null, null, null));

        assertNull(repository.setting.loginFailureThresholdOverride);
        assertNull(repository.setting.loginInitialBlockOverride);
        assertNull(repository.setting.loginMaxBlockOverride);
        assertNull(repository.setting.registrationChallengeEnabledOverride);
        assertNull(repository.setting.registrationChallengeTtlOverride);
        assertNull(repository.setting.registrationChallengeProviderOverride);
        assertNull(repository.setting.registrationTurnstileSiteKeyOverride);
        assertNull(repository.setting.geoIpEnabledOverride);
        assertNull(repository.setting.geoIpPrimaryProviderOverride);
        assertEquals(5, view.effectiveLoginFailureThreshold());
        assertEquals("ALTCHA", view.effectiveRegistrationChallengeProvider());
        assertEquals("IPWHOIS", view.effectiveGeoIpPrimaryProvider());
    }

    @Test
    void rejectsWhenMaximumBlockIsShorterThanInitialBlock() {
        AuthSecuritySettingsService service = service(new TestConfig(), new InMemorySystemAuthSecuritySettingRepository(null));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(new UpdateAuthSecuritySettingsRequest(
                        5, "PT30M", "PT10M",
                        Boolean.TRUE, "PT10M", "ALTCHA",
                        null, null, null, null,
                        null, null, null, null, null, null, null)));

        assertEquals("Maximum login block must be greater than or equal to the initial login block", error.getMessage());
    }

    @Test
    void rejectsTurnstileProviderWithoutConfiguredSecret() {
        AuthSecuritySettingsService service = service(new TestConfig(), new InMemorySystemAuthSecuritySettingRepository(null));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(new UpdateAuthSecuritySettingsRequest(
                        null, null, null,
                        Boolean.TRUE, null, "TURNSTILE",
                        "site-key", null, null, null,
                        null, null, null, null, null, null, null)));

        assertEquals("TURNSTILE requires both a site key and secret before it can be enabled", error.getMessage());
    }

    @Test
    void rejectsGeoIpFallbacksThatRepeatPrimaryProvider() {
        AuthSecuritySettingsService service = service(new TestConfig(), new InMemorySystemAuthSecuritySettingRepository(null));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(new UpdateAuthSecuritySettingsRequest(
                        null, null, null,
                        null, null, null,
                        null, null, null, null,
                        Boolean.TRUE, "IPWHOIS", "IPWHOIS,IP_API", null, null, null, null)));

        assertEquals("Geo-IP fallback providers must not repeat the primary provider", error.getMessage());
    }

    @Test
    void storesIpinfoTokenWhenSecureStorageIsConfigured() {
        InMemorySystemAuthSecuritySettingRepository repository = new InMemorySystemAuthSecuritySettingRepository(null);
        AuthSecuritySettingsService service = service(new TestConfig(), repository);

        AuthSecuritySettingsView view = service.update(new UpdateAuthSecuritySettingsRequest(
                null, null, null,
                null, null, null,
                null, null, null, null,
                Boolean.TRUE, "IPWHOIS", "IPAPI_CO,IPINFO_LITE", null, null, null, "token-123"));

        assertTrue(repository.setting.geoIpIpinfoTokenCiphertext != null && !repository.setting.geoIpIpinfoTokenCiphertext.isBlank());
        assertTrue(view.geoIpIpinfoTokenConfigured());
    }

    private AuthSecuritySettingsService service(InboxBridgeConfig config, SystemAuthSecuritySettingRepository repository) {
        AuthSecuritySettingsService service = new AuthSecuritySettingsService();
        service.inboxBridgeConfig = config;
        service.repository = repository;
        service.secretEncryptionService = configuredSecretEncryptionService();
        return service;
    }

    private SecretEncryptionService configuredSecretEncryptionService() {
        SecretEncryptionService service = new SecretEncryptionService();
        service.tokenEncryptionKey = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        service.tokenEncryptionKeyId = "test-key";
        return service;
    }

    private SecretEncryptionService passthroughSecrets() {
        return new SecretEncryptionService() {
            @Override
            public boolean isConfigured() {
                return true;
            }

            @Override
            public String decrypt(String ciphertextBase64, String nonceBase64, String keyVersion, String context) {
                return ciphertextBase64;
            }
        };
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
            return 0.2;
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

                        @Override
                        public String registrationChallengeProvider() {
                            return "ALTCHA";
                        }

                        @Override
                        public RegistrationCaptcha registrationCaptcha() {
                            return new RegistrationCaptcha() {
                                @Override
                                public Altcha altcha() {
                                    return new Altcha() {
                                        @Override
                                        public long maxNumber() {
                                            return 1000;
                                        }

                                        @Override
                                        public Optional<String> hmacKey() {
                                            return Optional.empty();
                                        }
                                    };
                                }

                                @Override
                                public Turnstile turnstile() {
                                    return new Turnstile() {
                                        @Override
                                        public Optional<String> siteKey() {
                                            return Optional.of("replace-me");
                                        }

                                        @Override
                                        public Optional<String> secret() {
                                            return Optional.of("replace-me");
                                        }
                                    };
                                }

                                @Override
                                public Hcaptcha hcaptcha() {
                                    return new Hcaptcha() {
                                        @Override
                                        public Optional<String> siteKey() {
                                            return Optional.of("replace-me");
                                        }

                                        @Override
                                        public Optional<String> secret() {
                                            return Optional.of("replace-me");
                                        }
                                    };
                                }
                            };
                        }

                        @Override
                        public GeoIp geoIp() {
                            return new GeoIp() {
                                @Override
                                public boolean enabled() {
                                    return false;
                                }

                                @Override
                                public String primaryProvider() {
                                    return "IPWHOIS";
                                }

                                @Override
                                public String fallbackProviders() {
                                    return "IPAPI_CO,IP_API,IPINFO_LITE";
                                }

                                @Override
                                public Duration cacheTtl() {
                                    return Duration.ofDays(30);
                                }

                                @Override
                                public Duration providerCooldown() {
                                    return Duration.ofMinutes(5);
                                }

                                @Override
                                public Duration requestTimeout() {
                                    return Duration.ofSeconds(3);
                                }

                                @Override
                                public Optional<String> ipinfoToken() {
                                    return Optional.empty();
                                }
                            };
                        }
                    };
                }

                @Override
                public Passkeys passkeys() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Remote remote() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public Gmail gmail() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Microsoft microsoft() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<Source> sources() {
            return java.util.List.of();
        }
    }
}
