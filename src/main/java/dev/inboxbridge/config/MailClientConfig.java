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

    @WithDefault("PT25M")
    Duration idleRenewalInterval();

    @WithDefault("PT20S")
    Duration idleRenewalTimeout();

    @WithDefault("PT30M")
    Duration idleSafetyPollInterval();

    @WithDefault("true")
    boolean idleSafetyPollingEnabled();

    @WithDefault("PT0S")
    Duration idleOperationTimeout();
}
