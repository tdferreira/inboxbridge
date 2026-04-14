package dev.inboxbridge.config;

import java.util.Optional;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "security")
public interface SecurityTokenConfig {

    @WithDefault("LOCAL")
    String providerMode();

    @WithDefault("replace-me")
    String tokenEncryptionKey();

    @WithDefault("v1")
    String tokenEncryptionKeyId();

    Optional<String> tokenEncryptionLegacyKeys();

    Optional<String> openbaoUrl();

    Optional<String> openbaoToken();

    Optional<String> openbaoMount();

    Optional<String> openbaoKey();

    Optional<String> vaultUrl();

    Optional<String> vaultToken();

    Optional<String> vaultMount();

    Optional<String> vaultKey();

    Optional<String> splitSecondaryMode();
}
