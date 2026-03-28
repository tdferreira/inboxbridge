package dev.inboxbridge.service;

import java.util.List;

import dev.inboxbridge.config.BridgeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Normalizes environment-managed source configuration so placeholder fallback
 * values from application.yaml do not appear as real fetchers when the
 * deployment has not configured any MAIL_ACCOUNT_* variables.
 */
@ApplicationScoped
public class EnvSourceService {

    private static final String DEFAULT_SOURCE_ID = "source-0";
    private static final String DEFAULT_HOST = "imap.example.com";
    private static final String DEFAULT_USERNAME = "replace-me@example.com";
    private static final String DEFAULT_PASSWORD = "replace-me";
    private static final String DEFAULT_FOLDER = "INBOX";
    private static final String DEFAULT_CUSTOM_LABEL = "Imported/Source0";

    @Inject
    BridgeConfig config;

    public void setConfigForTest(BridgeConfig config) {
        this.config = config;
    }

    public List<IndexedSource> configuredSources() {
        List<BridgeConfig.Source> sources = config.sources();
        return java.util.stream.IntStream.range(0, sources.size())
                .mapToObj(index -> new IndexedSource(index, sources.get(index)))
                .filter(indexed -> isConfigured(indexed.source()))
                .toList();
    }

    public boolean isConfigured(BridgeConfig.Source source) {
        return !DEFAULT_SOURCE_ID.equals(source.id())
                || source.enabled()
                || source.protocol() != BridgeConfig.Protocol.IMAP
                || !DEFAULT_HOST.equals(source.host())
                || source.port() != 993
                || !source.tls()
                || source.authMethod() != BridgeConfig.AuthMethod.PASSWORD
                || source.oauthProvider() != BridgeConfig.OAuthProvider.NONE
                || !DEFAULT_USERNAME.equals(source.username())
                || !DEFAULT_PASSWORD.equals(source.password())
                || source.oauthRefreshToken().isPresent() && !source.oauthRefreshToken().orElse("").isBlank()
                || !DEFAULT_FOLDER.equals(source.folder().orElse(DEFAULT_FOLDER))
                || source.unreadOnly()
                || !DEFAULT_CUSTOM_LABEL.equals(source.customLabel().orElse(DEFAULT_CUSTOM_LABEL));
    }

    public record IndexedSource(int index, BridgeConfig.Source source) {
    }
}
