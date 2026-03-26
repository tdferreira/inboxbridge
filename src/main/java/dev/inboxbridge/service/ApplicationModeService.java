package dev.inboxbridge.service;

import dev.inboxbridge.config.BridgeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Centralizes deployment-level mode flags so resources can consistently apply
 * single-user or multi-user behavior.
 */
@ApplicationScoped
public class ApplicationModeService {

    @Inject
    BridgeConfig bridgeConfig;

    public boolean multiUserEnabled() {
        return bridgeConfig.multiUserEnabled();
    }

    public void requireMultiUserMode() {
        if (!multiUserEnabled()) {
            throw new IllegalArgumentException("Multi-user mode is disabled for this deployment.");
        }
    }
}
