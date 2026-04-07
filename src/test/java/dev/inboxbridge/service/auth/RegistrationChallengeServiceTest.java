package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.RegistrationChallengeResponse;

class RegistrationChallengeServiceTest {

    @Test
    void currentChallengeReturnsAltchaPayloadByDefault() {
        RegistrationChallengeService service = new RegistrationChallengeService();
        service.authSecuritySettingsService = authSecuritySettingsService(true, "ALTCHA");
        service.inboxBridgeConfig = new TestConfig();
        service.objectMapper = new ObjectMapper();

        RegistrationChallengeResponse response = service.currentChallenge();

        assertTrue(response.enabled());
        assertEquals("ALTCHA", response.provider());
        assertNotNull(response.altcha());
        assertEquals("SHA-256", response.altcha().algorithm());
        assertTrue(response.altcha().maxNumber() > 0);
    }

    @Test
    void validateAndConsumeRejectsInvalidAltchaPayload() {
        RegistrationChallengeService service = new RegistrationChallengeService();
        service.authSecuritySettingsService = authSecuritySettingsService(true, "ALTCHA");
        service.inboxBridgeConfig = new TestConfig();
        service.objectMapper = new ObjectMapper();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.validateAndConsume("invalid-payload", "203.0.113.7"));

        assertEquals("Registration CAPTCHA is invalid or expired", error.getMessage());
    }

    @Test
    void disabledModeSkipsCaptchaValidation() {
        RegistrationChallengeService service = new RegistrationChallengeService();
        service.authSecuritySettingsService = authSecuritySettingsService(false, "ALTCHA");
        service.inboxBridgeConfig = new TestConfig();
        service.objectMapper = new ObjectMapper();

        assertEquals(false, service.currentChallenge().enabled());
        service.validateAndConsume(null, null);
    }

    private AuthSecuritySettingsService authSecuritySettingsService(boolean enabled, String provider) {
        return new AuthSecuritySettingsService() {
            @Override
            public EffectiveAuthSecuritySettings effectiveSettings() {
                return new EffectiveAuthSecuritySettings(
                        5,
                        Duration.ofMinutes(5),
                        Duration.ofHours(1),
                        enabled,
                        Duration.ofMinutes(10),
                        provider,
                        "",
                        "",
                        "",
                        "",
                        false,
                        "IPWHOIS",
                        "IPAPI_CO,IP_API,IPINFO_LITE",
                        Duration.ofDays(30),
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(3),
                        "");
            }
        };
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
                                            return Optional.empty();
                                        }

                                        @Override
                                        public Optional<String> secret() {
                                            return Optional.empty();
                                        }
                                    };
                                }

                                @Override
                                public Hcaptcha hcaptcha() {
                                    return new Hcaptcha() {
                                        @Override
                                        public Optional<String> siteKey() {
                                            return Optional.empty();
                                        }

                                        @Override
                                        public Optional<String> secret() {
                                            return Optional.empty();
                                        }
                                    };
                                }
                            };
                        }

                        @Override
                        public GeoIp geoIp() {
                            throw new UnsupportedOperationException();
                        }
                    };
                }

                @Override
                public Passkeys passkeys() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Remote remote() {
                    return new Remote() {
                        @Override
                        public boolean enabled() {
                            return true;
                        }

                        @Override
                        public Duration sessionTtl() {
                            return Duration.ofHours(12);
                        }

                        @Override
                        public int pollRateLimitCount() {
                            return 60;
                        }

                        @Override
                        public Duration pollRateLimitWindow() {
                            return Duration.ofMinutes(1);
                        }

                        @Override
                        public Optional<String> serviceToken() {
                            return Optional.empty();
                        }

                        @Override
                        public Optional<String> serviceUsername() {
                            return Optional.empty();
                        }
                    };
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
