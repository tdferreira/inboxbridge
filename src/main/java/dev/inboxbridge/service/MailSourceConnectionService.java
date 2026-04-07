package dev.inboxbridge.service;

import org.jboss.logging.Logger;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;

/**
 * Owns authenticated source mailbox connections, including OAuth provider
 * detection and one-time token-refresh retries for retryable session failures.
 */
@ApplicationScoped
public class MailSourceConnectionService {

    private static final Logger LOG = Logger.getLogger(MailSourceConnectionService.class);

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    GoogleOAuthService googleOAuthService;

    public void connectStore(Store store, InboxBridgeConfig.Source source) throws MessagingException {
        if (usesMicrosoftOAuth(source)) {
            connectStoreWithMicrosoftOAuthRetry(
                    store,
                    source.id(),
                    source.host(),
                    source.port(),
                    source.username(),
                    () -> microsoftOAuthService.getAccessToken(source));
            return;
        }
        if (usesGoogleOAuth(source)) {
            connectStoreWithGoogleOAuthRetry(
                    store,
                    source.id(),
                    source.host(),
                    source.port(),
                    source.username(),
                    () -> googleOAuthService.getAccessToken(source));
            return;
        }
        store.connect(source.host(), source.port(), source.username(), source.password());
    }

    public void connectStore(Store store, RuntimeEmailAccount bridge) throws MessagingException {
        if (usesMicrosoftOAuth(bridge)) {
            connectStoreWithMicrosoftOAuthRetry(
                    store,
                    bridge.id(),
                    bridge.host(),
                    bridge.port(),
                    bridge.username(),
                    () -> microsoftOAuthService.getAccessToken(bridge));
            return;
        }
        if (usesGoogleOAuth(bridge)) {
            connectStoreWithGoogleOAuthRetry(
                    store,
                    bridge.id(),
                    bridge.host(),
                    bridge.port(),
                    bridge.username(),
                    () -> googleOAuthService.getAccessToken(bridge));
            return;
        }
        store.connect(bridge.host(), bridge.port(), bridge.username(), bridge.password());
    }

    private void connectStoreWithMicrosoftOAuthRetry(
            Store store,
            String sourceId,
            String host,
            int port,
            String username,
            TokenSupplier tokenSupplier) throws MessagingException {
        try {
            store.connect(host, port, username, tokenSupplier.get());
        } catch (MessagingException firstFailure) {
            if (!MailSourceClient.isRetryableMicrosoftOAuthFailure(firstFailure)) {
                throw firstFailure;
            }
            LOG.warnf(firstFailure,
                    "Microsoft session for %s was rejected; invalidating the cached token and retrying once",
                    sourceId);
            microsoftOAuthService.invalidateCachedToken(sourceId);
            store.connect(host, port, username, tokenSupplier.get());
        }
    }

    private void connectStoreWithGoogleOAuthRetry(
            Store store,
            String sourceId,
            String host,
            int port,
            String username,
            TokenSupplier tokenSupplier) throws MessagingException {
        try {
            store.connect(host, port, username, tokenSupplier.get());
        } catch (MessagingException firstFailure) {
            if (!MailSourceClient.isRetryableMicrosoftOAuthFailure(firstFailure)) {
                throw firstFailure;
            }
            LOG.warnf(firstFailure,
                    "Google session for %s was rejected; invalidating the cached token and retrying once",
                    sourceId);
            googleOAuthService.clearCachedToken("source-google:" + sourceId);
            store.connect(host, port, username, tokenSupplier.get());
        }
    }

    private boolean usesMicrosoftOAuth(InboxBridgeConfig.Source source) {
        return source.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && source.oauthProvider() == InboxBridgeConfig.OAuthProvider.MICROSOFT;
    }

    private boolean usesGoogleOAuth(InboxBridgeConfig.Source source) {
        return source.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && source.oauthProvider() == InboxBridgeConfig.OAuthProvider.GOOGLE;
    }

    private boolean usesMicrosoftOAuth(RuntimeEmailAccount bridge) {
        return bridge.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && bridge.oauthProvider() == InboxBridgeConfig.OAuthProvider.MICROSOFT;
    }

    private boolean usesGoogleOAuth(RuntimeEmailAccount bridge) {
        return bridge.authMethod() == InboxBridgeConfig.AuthMethod.OAUTH2
                && bridge.oauthProvider() == InboxBridgeConfig.OAuthProvider.GOOGLE;
    }

    @FunctionalInterface
    private interface TokenSupplier {
        String get();
    }
}
