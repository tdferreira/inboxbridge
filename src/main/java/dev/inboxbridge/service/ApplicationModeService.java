package dev.inboxbridge.service;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.persistence.AppUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Centralizes deployment-level mode flags so resources can consistently apply
 * single-user or multi-user behavior.
 */
@ApplicationScoped
public class ApplicationModeService {

    @Inject
    BridgeConfig bridgeConfig;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    AppUserService appUserService;

    public boolean multiUserEnabled() {
        Boolean override = systemOAuthAppSettingsService.multiUserEnabledOverride();
        return override != null ? override : bridgeConfig.multiUserEnabled();
    }

    public void requireMultiUserMode() {
        if (!multiUserEnabled()) {
            throw new IllegalArgumentException("Multi-user mode is disabled for this deployment.");
        }
    }

    @Transactional
    public void setMultiUserEnabled(AppUser actor, boolean enabled) {
        if (enabled) {
            systemOAuthAppSettingsService.setMultiUserEnabledOverride(Boolean.TRUE);
            appUserService.switchToMultiUserMode();
            return;
        }
        appUserService.switchToSingleUserMode(actor);
        systemOAuthAppSettingsService.setMultiUserEnabledOverride(Boolean.FALSE);
    }
}
