package dev.inboxbridge.config;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.domain.SourceFetchMode;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "inboxbridge")
public interface InboxBridgeConfig {

    @WithDefault("true")
    boolean pollEnabled();

    @WithDefault("5m")
    String pollInterval();

    @WithDefault("50")
    int fetchWindow();

    @WithDefault("PT1S")
    Duration sourceHostMinSpacing();

    @WithDefault("2")
    int sourceHostMaxConcurrency();

    @WithDefault("PT0.25S")
    Duration destinationProviderMinSpacing();

    @WithDefault("1")
    int destinationProviderMaxConcurrency();

    @WithDefault("PT2M")
    Duration throttleLeaseTtl();

    @WithDefault("6")
    int adaptiveThrottleMaxMultiplier();

    @WithDefault("0.2")
    double successJitterRatio();

    @WithDefault("PT30S")
    Duration maxSuccessJitter();

    @WithDefault("true")
    boolean multiUserEnabled();

    Security security();

    Gmail gmail();

    Microsoft microsoft();

    List<Source> sources();

    interface Gmail {
        @WithDefault("me")
        String destinationUser();

        String clientId();

        String clientSecret();

        String refreshToken();

        String redirectUri();

        @WithDefault("true")
        boolean createMissingLabels();

        @WithDefault("false")
        boolean neverMarkSpam();

        @WithDefault("false")
        boolean processForCalendar();
    }

    interface Security {
        Auth auth();

        Passkeys passkeys();

        Remote remote();

        interface Auth {
            @WithDefault("5")
            int loginFailureThreshold();

            @WithDefault("PT5M")
            Duration loginInitialBlock();

            @WithDefault("PT1H")
            Duration loginMaxBlock();

        @WithDefault("true")
        boolean registrationChallengeEnabled();

        @WithDefault("PT10M")
        Duration registrationChallengeTtl();

        @WithDefault("ALTCHA")
        String registrationChallengeProvider();

        RegistrationCaptcha registrationCaptcha();

            GeoIp geoIp();

            interface RegistrationCaptcha {
                Altcha altcha();

                Turnstile turnstile();

                Hcaptcha hcaptcha();

                interface Altcha {
                    @WithDefault("100000")
                    long maxNumber();

                    Optional<String> hmacKey();
                }

                interface Turnstile {
                    Optional<String> siteKey();

                    Optional<String> secret();
                }

                interface Hcaptcha {
                    Optional<String> siteKey();

                    Optional<String> secret();
                }
            }

            interface GeoIp {
                @WithDefault("false")
                boolean enabled();

                @WithDefault("IPWHOIS")
                String primaryProvider();

                @WithDefault("IPAPI_CO,IP_API,IPINFO_LITE")
                String fallbackProviders();

                @WithDefault("PT720H")
                Duration cacheTtl();

                @WithDefault("PT5M")
                Duration providerCooldown();

                @WithDefault("PT3S")
                Duration requestTimeout();

                Optional<String> ipinfoToken();
            }
        }

        interface Passkeys {
            @WithDefault("true")
            boolean enabled();

            @WithDefault("localhost")
            String rpId();

            @WithDefault("InboxBridge")
            String rpName();

            @WithDefault("https://localhost:3000")
            String origins();

            @WithDefault("PT5M")
            String challengeTtl();
        }

        interface Remote {
            @WithDefault("true")
            boolean enabled();

            @WithDefault("PT12H")
            Duration sessionTtl();

            @WithDefault("60")
            int pollRateLimitCount();

            @WithDefault("PT1M")
            Duration pollRateLimitWindow();

            Optional<String> serviceToken();

            Optional<String> serviceUsername();
        }
    }

    interface Microsoft {
        @WithDefault("consumers")
        String tenant();

        @WithDefault("replace-me")
        String clientId();

        @WithDefault("replace-me")
        String clientSecret();

        @WithDefault("http://localhost:8080/api/microsoft-oauth/callback")
        String redirectUri();
    }

    interface Source {
        String id();

        @WithDefault("true")
        boolean enabled();

        Protocol protocol();

        String host();

        int port();

        @WithDefault("true")
        boolean tls();

        @WithDefault("PASSWORD")
        AuthMethod authMethod();

        @WithDefault("NONE")
        OAuthProvider oauthProvider();

        String username();

        String password();

        Optional<String> oauthRefreshToken();

        Optional<String> folder();

        @WithDefault("false")
        boolean unreadOnly();

        @WithDefault("POLLING")
        SourceFetchMode fetchMode();

        Optional<String> customLabel();
    }

    enum Protocol {
        IMAP,
        POP3
    }

    enum AuthMethod {
        PASSWORD,
        OAUTH2
    }

    enum OAuthProvider {
        NONE,
        GOOGLE,
        MICROSOFT
    }
}
