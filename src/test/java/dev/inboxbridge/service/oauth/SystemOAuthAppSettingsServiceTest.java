package dev.inboxbridge.service.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.persistence.SystemOAuthAppSettingsRepository;
import jakarta.transaction.Transactional;

class SystemOAuthAppSettingsServiceTest {

    @Test
    void repositoryBackedReadMethodsRemainTransactional() throws NoSuchMethodException {
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("view").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleClientId").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleClientSecret").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleDestinationUser").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleRedirectUri").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleRefreshToken").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("microsoftClientId").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("microsoftClientSecret").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("multiUserEnabledOverride").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("effectiveMultiUserEnabled").isAnnotationPresent(Transactional.class));
    }

    @Test
    void googleRefreshTokenIgnoresEnvFallbackWhenPolicyDisablesEnvManagedMailboxSecrets() {
        SystemOAuthAppSettingsService service = new SystemOAuthAppSettingsService();
        service.setConfig(new TestConfig());
        service.setSecretManagementPolicyConfig(new dev.inboxbridge.config.SecretManagementPolicyConfig() {
            @Override
            public boolean allowEnvManagedMailboxSecrets() {
                return false;
            }

            @Override
            public java.time.Duration reencryptionCooldown() {
                return java.time.Duration.ofHours(12);
            }

            @Override
            public boolean allowImmediateReencryptOverride() {
                return false;
            }
        });
        service.setRepository(new EmptySystemOAuthAppSettingsRepository());

        assertEquals("", service.googleRefreshToken());
        assertEquals(true, service.envManagedGoogleRefreshTokenConfigured());
        assertEquals(false, service.envManagedMailboxSecretsAllowed());
    }

    private static final class EmptySystemOAuthAppSettingsRepository extends SystemOAuthAppSettingsRepository {
        @Override
        public java.util.Optional<dev.inboxbridge.persistence.SystemOAuthAppSettings> findSingleton() {
            return java.util.Optional.empty();
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
        public java.time.Duration sourceHostMinSpacing() {
            return java.time.Duration.ofSeconds(1);
        }

        @Override
        public int sourceHostMaxConcurrency() {
            return 2;
        }

        @Override
        public java.time.Duration destinationProviderMinSpacing() {
            return java.time.Duration.ofMillis(250);
        }

        @Override
        public int destinationProviderMaxConcurrency() {
            return 1;
        }

        @Override
        public java.time.Duration throttleLeaseTtl() {
            return java.time.Duration.ofMinutes(2);
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
        public java.time.Duration maxSuccessJitter() {
            return java.time.Duration.ofSeconds(30);
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
            return new Gmail() {
                @Override
                public String destinationUser() {
                    return "me";
                }

                @Override
                public String clientId() {
                    return "client-id";
                }

                @Override
                public String clientSecret() {
                    return "client-secret";
                }

                @Override
                public String refreshToken() {
                    return "env-refresh-token";
                }

                @Override
                public String redirectUri() {
                    return "https://localhost:3000/api/google-oauth/callback";
                }

                @Override
                public boolean createMissingLabels() {
                    return true;
                }

                @Override
                public boolean neverMarkSpam() {
                    return false;
                }

                @Override
                public boolean processForCalendar() {
                    return false;
                }
            };
        }

        @Override
        public Microsoft microsoft() {
            return new Microsoft() {
                @Override
                public String tenant() {
                    return "consumers";
                }

                @Override
                public String clientId() {
                    return "microsoft-client-id";
                }

                @Override
                public String clientSecret() {
                    return "microsoft-client-secret";
                }

                @Override
                public String redirectUri() {
                    return "https://localhost:3000/api/microsoft-oauth/callback";
                }
            };
        }

        @Override
        public java.util.List<Source> sources() {
            return java.util.List.of();
        }
    }
}
