package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.config.InboxBridgeConfig.Security.Auth;
import dev.inboxbridge.persistence.GeoIpCacheEntry;
import dev.inboxbridge.persistence.GeoIpCacheEntryRepository;
import dev.inboxbridge.service.auth.AuthSecuritySettingsService.EffectiveAuthSecuritySettings;

class GeoIpLocationServiceTest {

    @Test
    void usesPrimaryProviderAndCachesSuccessfulLookups() {
        InMemoryGeoIpCacheEntryRepository repository = new InMemoryGeoIpCacheEntryRepository();
        GeoIpLocationService service = service(repository);
        service.httpClient = new FakeHttpClient(
                new FakeHttpResponse(200, "{\"success\":true,\"city\":\"Lisbon\",\"region\":\"Lisbon\",\"country\":\"Portugal\"}"));

        Optional<String> resolved = service.resolveLocation("203.0.113.10");
        Optional<String> cached = service.resolveLocation("203.0.113.10");

        assertEquals(Optional.of("Lisbon, Portugal"), resolved);
        assertEquals(Optional.of("Lisbon, Portugal"), cached);
        assertEquals(1, repository.entries.size());
    }

    @Test
    void fallsBackWhenPrimaryProviderIsRateLimited() {
        InMemoryGeoIpCacheEntryRepository repository = new InMemoryGeoIpCacheEntryRepository();
        GeoIpLocationService service = service(repository);
        InboxBridgeConfig config = config(true, "IPWHOIS", "IPINFO_LITE", Duration.ofDays(30), Duration.ofMinutes(5), Duration.ofSeconds(3), Optional.of("test-token"));
        service.authSecuritySettingsService = authSecuritySettingsService(true, "IPWHOIS", "IPINFO_LITE", Duration.ofDays(30), Duration.ofMinutes(5), Duration.ofSeconds(3), Optional.of("test-token"), config);
        service.inboxBridgeConfig = config;
        service.httpClient = new FakeHttpClient(
                new FakeHttpResponse(429, "{\"success\":false}"),
                new FakeHttpResponse(200, "{\"country\":\"France\"}"));

        Optional<String> resolved = service.resolveLocation("198.51.100.12");

        assertEquals(Optional.of("France"), resolved);
        assertTrue(service.providerCoolingDown(GeoIpLocationService.GeoIpProvider.IPWHOIS, Instant.now()));
    }

    @Test
    void skipsGeoLookupForPrivateAddressesAndWhenDisabled() {
        GeoIpLocationService disabled = service(new InMemoryGeoIpCacheEntryRepository());
        InboxBridgeConfig disabledConfig = config(false, "IPWHOIS", "IPAPI_CO,IP_API,IPINFO_LITE", Duration.ofDays(30), Duration.ofMinutes(5), Duration.ofSeconds(3), Optional.empty());
        disabled.authSecuritySettingsService = authSecuritySettingsService(false, "IPWHOIS", "IPAPI_CO,IP_API,IPINFO_LITE", Duration.ofDays(30), Duration.ofMinutes(5), Duration.ofSeconds(3), Optional.empty(), disabledConfig);
        disabled.inboxBridgeConfig = disabledConfig;
        assertFalse(disabled.isConfigured());
        assertEquals(Optional.empty(), disabled.resolveLocation("203.0.113.10"));

        GeoIpLocationService service = service(new InMemoryGeoIpCacheEntryRepository());
        assertEquals(Optional.empty(), service.resolveLocation("127.0.0.1"));
        assertEquals(Optional.empty(), service.resolveLocation("10.0.0.5"));
        assertEquals(Optional.empty(), service.resolveLocation("unknown"));
    }

    @Test
    void usesIpapiCoFallbackWithoutToken() {
        InMemoryGeoIpCacheEntryRepository repository = new InMemoryGeoIpCacheEntryRepository();
        GeoIpLocationService service = service(repository);
        InboxBridgeConfig config = config(true, "IPWHOIS", "IPAPI_CO,IP_API", Duration.ofDays(30), Duration.ofMinutes(5), Duration.ofSeconds(3), Optional.empty());
        service.authSecuritySettingsService = authSecuritySettingsService(true, "IPWHOIS", "IPAPI_CO,IP_API", Duration.ofDays(30), Duration.ofMinutes(5), Duration.ofSeconds(3), Optional.empty(), config);
        service.inboxBridgeConfig = config;
        service.httpClient = new FakeHttpClient(
                new FakeHttpResponse(503, "{\"success\":false}"),
                new FakeHttpResponse(200, "{\"city\":\"Porto\",\"region\":\"Porto\",\"country_name\":\"Portugal\"}"));

        Optional<String> resolved = service.resolveLocation("198.51.100.44");

        assertEquals(Optional.of("Porto, Portugal"), resolved);
    }

    private GeoIpLocationService service(GeoIpCacheEntryRepository repository) {
        GeoIpLocationService service = new GeoIpLocationService();
        service.repository = repository;
        service.objectMapper = new ObjectMapper();
        InboxBridgeConfig config = config(true, "IPWHOIS", "IPAPI_CO,IP_API,IPINFO_LITE", Duration.ofDays(30), Duration.ofMinutes(5), Duration.ofSeconds(3), Optional.empty());
        service.authSecuritySettingsService = authSecuritySettingsService(true, "IPWHOIS", "IPAPI_CO,IP_API,IPINFO_LITE", Duration.ofDays(30), Duration.ofMinutes(5), Duration.ofSeconds(3), Optional.empty(), config);
        service.inboxBridgeConfig = config;
        return service;
    }

    private AuthSecuritySettingsService authSecuritySettingsService(
            boolean enabled,
            String primaryProvider,
            String fallbackProviders,
            Duration cacheTtl,
            Duration providerCooldown,
            Duration requestTimeout,
            Optional<String> ipinfoToken,
            InboxBridgeConfig config) {
        AuthSecuritySettingsService service = new AuthSecuritySettingsService() {
            @Override
            public EffectiveAuthSecuritySettings effectiveSettings() {
                return new EffectiveAuthSecuritySettings(
                        5,
                        Duration.ofMinutes(5),
                        Duration.ofHours(1),
                        true,
                        Duration.ofMinutes(10),
                        "ALTCHA",
                        "",
                        "",
                        "",
                        "",
                        enabled,
                        primaryProvider,
                        fallbackProviders,
                        cacheTtl,
                        providerCooldown,
                        requestTimeout,
                        ipinfoToken.orElse(""));
            }
        };
        service.setConfig(config);
        return service;
    }

    private InboxBridgeConfig config(
            boolean enabled,
            String primaryProvider,
            String fallbackProviders,
            Duration cacheTtl,
            Duration providerCooldown,
            Duration requestTimeout,
            Optional<String> ipinfoToken) {
        return new dev.inboxbridge.config.InboxBridgeConfig() {
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
                                        return enabled;
                                    }

                                    @Override
                                    public String primaryProvider() {
                                        return primaryProvider;
                                    }

                                    @Override
                                    public String fallbackProviders() {
                                        return fallbackProviders;
                                    }

                                    @Override
                                    public Duration cacheTtl() {
                                        return cacheTtl;
                                    }

                                    @Override
                                    public Duration providerCooldown() {
                                        return providerCooldown;
                                    }

                                    @Override
                                    public Duration requestTimeout() {
                                        return requestTimeout;
                                    }

                                    @Override
                                    public Optional<String> ipinfoToken() {
                                        return ipinfoToken;
                                    }
                                };
                            }
                        };
                    }

                    @Override
                    public Passkeys passkeys() {
                        return null;
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
            public java.util.List<Source> sources() {
                return java.util.List.of();
            }

            private Auth.RegistrationCaptcha captchaDefaults() {
                return new Auth.RegistrationCaptcha() {
                    @Override
                    public Auth.RegistrationCaptcha.Altcha altcha() {
                        return new Auth.RegistrationCaptcha.Altcha() {
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
                    public Auth.RegistrationCaptcha.Turnstile turnstile() {
                        return new Auth.RegistrationCaptcha.Turnstile() {
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
                    public Auth.RegistrationCaptcha.Hcaptcha hcaptcha() {
                        return new Auth.RegistrationCaptcha.Hcaptcha() {
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
        };
    }

    private static final class InMemoryGeoIpCacheEntryRepository extends GeoIpCacheEntryRepository {
        private final ConcurrentHashMap<String, GeoIpCacheEntry> entries = new ConcurrentHashMap<>();

        @Override
        public Optional<GeoIpCacheEntry> findValid(String ipAddress, Instant now) {
            return Optional.ofNullable(entries.get(ipAddress))
                    .filter(entry -> now.isBefore(entry.expiresAt));
        }

        @Override
        public void persist(GeoIpCacheEntry entity) {
            entries.put(entity.ipAddress, entity);
        }

        @Override
        public void deleteExpired(Instant now) {
            entries.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt));
        }

        @Override
        public Optional<GeoIpCacheEntry> findByIpAddress(String ipAddress) {
            return Optional.ofNullable(entries.get(ipAddress));
        }
    }

    private static final class FakeHttpClient extends HttpClient {
        private final Queue<HttpResponse<String>> responses = new ConcurrentLinkedQueue<>();

        private FakeHttpClient(HttpResponse<String>... responses) {
            for (HttpResponse<String> response : responses) {
                this.responses.add(response);
            }
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(3));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return (HttpResponse<T>) responses.poll();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeHttpResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return HttpRequest.newBuilder(URI.create("https://example.test")).build();
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (_a, _b) -> true);
        }

        @Override
        public URI uri() {
            return URI.create("https://example.test");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

    }
}
