package dev.inboxbridge.config;

import java.time.Duration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "inboxbridge.security.secret-management")
public interface SecretManagementPolicyConfig {

    @WithDefault("true")
    boolean allowEnvManagedMailboxSecrets();

    @WithDefault("PT12H")
    Duration reencryptionCooldown();

    @WithDefault("false")
    boolean allowImmediateReencryptOverride();

    @WithDefault("PT10M")
    Duration reauthenticationTtl();
}
