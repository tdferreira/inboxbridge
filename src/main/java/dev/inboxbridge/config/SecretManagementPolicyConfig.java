package dev.inboxbridge.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "inboxbridge.security.secret-management")
public interface SecretManagementPolicyConfig {

    @WithDefault("true")
    boolean allowEnvManagedMailboxSecrets();
}
