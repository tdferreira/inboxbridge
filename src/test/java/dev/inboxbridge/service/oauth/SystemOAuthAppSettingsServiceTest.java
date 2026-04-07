package dev.inboxbridge.service.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import jakarta.transaction.Transactional;

class SystemOAuthAppSettingsServiceTest {

    @Test
    void repositoryBackedReadMethodsRemainTransactional() throws NoSuchMethodException {
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("view").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleClientId").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleClientSecret").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleDestinationUser").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleRedirectUri").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("googleRefreshToken").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("microsoftClientId").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("microsoftClientSecret").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("multiUserEnabledOverride").isAnnotationPresent(Transactional.class));
        assertEquals(true, SystemOAuthAppSettingsService.class.getMethod("effectiveMultiUserEnabled").isAnnotationPresent(Transactional.class));
    }
}
