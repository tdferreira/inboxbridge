package dev.inboxbridge.service;

import java.util.ArrayList;
import java.util.List;

import dev.inboxbridge.config.InboxBridgeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OAuthProviderRegistryService {

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    public List<InboxBridgeConfig.OAuthProvider> configuredSourceProviders() {
        List<InboxBridgeConfig.OAuthProvider> providers = new ArrayList<>();
        if (systemOAuthAppSettingsService.googleClientConfigured()) {
            providers.add(InboxBridgeConfig.OAuthProvider.GOOGLE);
        }
        if (systemOAuthAppSettingsService.microsoftClientConfigured()) {
            providers.add(InboxBridgeConfig.OAuthProvider.MICROSOFT);
        }
        return providers;
    }
}
