package dev.inboxbridge.service;

import java.util.ArrayList;
import java.util.List;

import dev.inboxbridge.config.BridgeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OAuthProviderRegistryService {

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    public List<BridgeConfig.OAuthProvider> configuredSourceProviders() {
        List<BridgeConfig.OAuthProvider> providers = new ArrayList<>();
        if (systemOAuthAppSettingsService.googleClientConfigured()) {
            providers.add(BridgeConfig.OAuthProvider.GOOGLE);
        }
        if (systemOAuthAppSettingsService.microsoftClientConfigured()) {
            providers.add(BridgeConfig.OAuthProvider.MICROSOFT);
        }
        return providers;
    }
}
