package dev.inboxbridge.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "inboxbridge.security.extension")
public interface ExtensionSecurityConfig {

    @WithDefault("PT1H")
    Duration accessTokenTtl();

    @WithDefault("PT720H")
    Duration refreshTokenTtl();
}
