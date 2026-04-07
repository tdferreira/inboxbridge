package dev.inboxbridge.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "inboxbridge.mail")
public interface MailClientConfig {

    @WithDefault("PT20S")
    Duration connectionTimeout();

    @WithDefault("PT20S")
    Duration operationTimeout();

    @WithDefault("PT0S")
    Duration idleOperationTimeout();
}
