package dev.inboxbridge.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "security")
public interface SecurityTokenConfig {

    @WithDefault("replace-me")
    String tokenEncryptionKey();

    @WithDefault("v1")
    String tokenEncryptionKeyId();

    Optional<String> tokenEncryptionLegacyKeys();
}
