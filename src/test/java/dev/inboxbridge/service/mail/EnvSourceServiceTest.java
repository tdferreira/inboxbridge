package dev.inboxbridge.service.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.SourceFetchMode;

class EnvSourceServiceTest {

    @Test
    void configuredSourcesIgnoresPurePlaceholderFallback() {
        EnvSourceService service = new EnvSourceService();
        service.setConfigForTest(new TestConfig(List.of(new TestSource(
                "source-0",
                false,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "replace-me@example.com",
                "replace-me",
                Optional.empty(),
                Optional.of("INBOX"),
                false,
                Optional.of("Imported/Source0")))));

        assertTrue(service.configuredSources().isEmpty());
        assertFalse(service.isConfigured(service.config.sources().getFirst()));
    }

    @Test
    void configuredSourcesKeepsRealEnvManagedSource() {
        EnvSourceService service = new EnvSourceService();
        service.setConfigForTest(new TestConfig(List.of(new TestSource(
                "outlook-main-imap",
                false,
                InboxBridgeConfig.Protocol.IMAP,
                "outlook.office365.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.OAUTH2,
                InboxBridgeConfig.OAuthProvider.MICROSOFT,
                "person@example.com",
                "replace-me",
                Optional.of("refresh-token"),
                Optional.of("INBOX"),
                false,
                Optional.of("Imported/Outlook")))));

        List<EnvSourceService.IndexedSource> configured = service.configuredSources();

        assertEquals(1, configured.size());
        assertEquals("outlook-main-imap", configured.getFirst().source().id());
        assertTrue(service.isConfigured(configured.getFirst().source()));
    }

    private static final class TestConfig implements InboxBridgeConfig {
        private final List<Source> sources;

        private TestConfig(List<Source> sources) {
            this.sources = sources;
        }

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

                        @Override
                        public String registrationChallengeProvider() {
                            return "ALTCHA";
                        }

                        @Override
                        public RegistrationCaptcha registrationCaptcha() {
                            return captchaDefaults();
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
                                    return "IPINFO_LITE";
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
                                public java.util.Optional<String> ipinfoToken() {
                                    return java.util.Optional.empty();
                                }
                            };
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

                @Override
                public Remote remote() {
                    return remoteDefaults();
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
        public List<Source> sources() {
            return sources;
        }

        private InboxBridgeConfig.Security.Auth.RegistrationCaptcha captchaDefaults() {
            return new InboxBridgeConfig.Security.Auth.RegistrationCaptcha() {
                @Override
                public InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Altcha altcha() {
                    return new InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Altcha() {
                        @Override
                        public long maxNumber() {
                            return 100000L;
                        }

                        @Override
                        public Optional<String> hmacKey() {
                            return Optional.empty();
                        }
                    };
                }

                @Override
                public InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Turnstile turnstile() {
                    return new InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Turnstile() {
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
                public InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Hcaptcha hcaptcha() {
                    return new InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Hcaptcha() {
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

        private Security.Remote remoteDefaults() {
            return new Security.Remote() {
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
    }

    private record TestSource(
            String id,
            boolean enabled,
            InboxBridgeConfig.Protocol protocol,
            String host,
            int port,
            boolean tls,
            InboxBridgeConfig.AuthMethod authMethod,
            InboxBridgeConfig.OAuthProvider oauthProvider,
            String username,
            String password,
            Optional<String> oauthRefreshToken,
            Optional<String> folder,
            boolean unreadOnly,
            Optional<String> customLabel) implements InboxBridgeConfig.Source {

        @Override
        public SourceFetchMode fetchMode() {
            return SourceFetchMode.POLLING;
        }
    }
}
