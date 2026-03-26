package dev.connexa.inboxbridge.config;

import java.util.List;
import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "bridge")
public interface BridgeConfig {

    @WithDefault("true")
    boolean pollEnabled();

    @WithDefault("5m")
    String pollInterval();

    @WithDefault("50")
    int fetchWindow();

    Gmail gmail();

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

    interface Source {
        String id();

        @WithDefault("true")
        boolean enabled();

        Protocol protocol();

        String host();

        int port();

        @WithDefault("true")
        boolean tls();

        String username();

        String password();

        Optional<String> folder();

        @WithDefault("false")
        boolean unreadOnly();

        Optional<String> customLabel();
    }

    enum Protocol {
        IMAP,
        POP3
    }
}
