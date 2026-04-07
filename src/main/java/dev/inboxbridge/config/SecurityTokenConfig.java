package dev.inboxbridge.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "security")
public interface SecurityTokenConfig {

    @WithDefault("replace-me")
    String tokenEncryptionKey();

    @WithDefault("v1")
    String tokenEncryptionKeyId();
}
