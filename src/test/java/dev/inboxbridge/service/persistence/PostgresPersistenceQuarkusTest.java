package dev.inboxbridge.service.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.UpdateUserUiPreferenceRequest;
import dev.inboxbridge.dto.UserUiNotificationView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.ExtensionSessionRepository;
import dev.inboxbridge.persistence.UserUiPreference;
import dev.inboxbridge.persistence.UserUiPreferenceRepository;
import dev.inboxbridge.service.admin.AppUserService;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import dev.inboxbridge.service.user.UserUiPreferenceService;
import dev.inboxbridge.testsupport.PostgresQuarkusTestProfile;
import dev.inboxbridge.testsupport.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(PostgresQuarkusTestProfile.class)
@QuarkusTestResource(value = PostgresTestResource.class, restrictToAnnotatedClass = true)
class PostgresPersistenceQuarkusTest {

    @Inject
    AppUserService appUserService;

    @Inject
    UserUiPreferenceService userUiPreferenceService;

    @Inject
    UserUiPreferenceRepository userUiPreferenceRepository;

    @Inject
    ExtensionSessionService extensionSessionService;

    @Inject
    ExtensionSessionRepository extensionSessionRepository;

    @Test
    @TestTransaction
    void flywayBackedPostgresPersistsUiPreferencesAndNotificationHistory() {
        AppUser admin = adminUser();

        var updated = userUiPreferenceService.update(admin, new UpdateUserUiPreferenceRequest(
                true,
                true,
                true,
                false,
                true,
                false,
                false,
                true,
                false,
                true,
                false,
                true,
                true,
                false,
                true,
                false,
                List.of("sourceEmailAccounts", "destination"),
                List.of("globalStats", "userManagement"),
                "pt-PT",
                "DARK_BLUE",
                "YYYY-MM-DD HH:mm",
                "MANUAL",
                "Europe/Lisbon",
                List.of(
                        new UserUiNotificationView(
                                "notif-1",
                                java.util.Map.of("key", "notifications.syncFailed"),
                                java.util.Map.of("text", "Sync failed"),
                                "error",
                                "source-a",
                                "poll-errors",
                                1_711_111_111L,
                                Boolean.TRUE,
                                5_000L),
                        new UserUiNotificationView(
                                "notif-2",
                                java.util.Map.of("key", "notifications.syncOk"),
                                null,
                                "success",
                                null,
                                "poll-success",
                                1_711_111_222L,
                                Boolean.FALSE,
                                null))));

        UserUiPreference stored = userUiPreferenceRepository.findByUserId(admin.id).orElseThrow();

        assertTrue(updated.persistLayout());
        assertEquals("pt-PT", updated.language());
        assertEquals("DARK_BLUE", updated.themeMode());
        assertEquals("YYYY-MM-DD HH:mm", updated.dateFormat());
        assertEquals("MANUAL", updated.timezoneMode());
        assertEquals("Europe/Lisbon", updated.timezone());
        assertEquals(2, updated.notificationHistory().size());
        assertEquals("sourceEmailAccounts,destination,quickSetup,userPolling,remoteControl,userStats", reorderForStorage(updated.userSectionOrder()));
        assertEquals("globalStats,userManagement,adminQuickSetup,systemDashboard,oauthApps,authSecurity", reorderForStorage(updated.adminSectionOrder()));
        assertNotNull(stored.updatedAt);
        assertTrue(stored.notificationHistory.contains("\"notif-1\""));
        assertTrue(stored.notificationHistory.contains("\"notifications.syncFailed\""));
        assertEquals("pt-PT", stored.language);
        assertEquals("DARK_BLUE", stored.themeMode);
        assertEquals("Europe/Lisbon", stored.timezone);
    }

    @Test
    @TestTransaction
    void flywayBackedPostgresPersistsAndRotatesExtensionSessions() {
        AppUser admin = adminUser();

        var created = extensionSessionService.createAuthenticatedSession(admin, "Firefox on Mac", "firefox", "0.6.0");
        Long sessionId = created.session().id;

        assertNotNull(sessionId);
        assertTrue(created.accessToken().startsWith("ibx_"));
        assertTrue(created.refreshToken().startsWith("ibx_"));

        ExtensionSession storedBeforeRefresh = extensionSessionRepository.findByIdOptional(sessionId).orElseThrow();
        String originalTokenHash = storedBeforeRefresh.tokenHash;
        String originalRefreshTokenHash = storedBeforeRefresh.refreshTokenHash;

        var authenticated = extensionSessionService.authenticate(created.accessToken()).orElseThrow();
        var refreshed = extensionSessionService.refresh(created.refreshToken()).orElseThrow();
        ExtensionSession storedAfterRefresh = extensionSessionRepository.findByIdOptional(sessionId).orElseThrow();

        assertEquals(admin.id, authenticated.userId());
        assertEquals(sessionId, refreshed.session().id);
        assertNotEquals(created.accessToken(), refreshed.accessToken());
        assertNotEquals(created.refreshToken(), refreshed.refreshToken());
        assertNotEquals(originalTokenHash, storedAfterRefresh.tokenHash);
        assertNotEquals(originalRefreshTokenHash, storedAfterRefresh.refreshTokenHash);
        assertNotNull(storedAfterRefresh.lastUsedAt);
        assertFalse(extensionSessionService.authenticate(created.accessToken()).isPresent());
        assertTrue(extensionSessionService.authenticate(refreshed.accessToken()).isPresent());

        List<Long> revokedIds = extensionSessionService.revokeAllSessions(admin);
        ExtensionSession revoked = extensionSessionRepository.findByIdOptional(sessionId).orElseThrow();

        assertEquals(List.of(sessionId), revokedIds);
        assertNotNull(revoked.revokedAt);
        assertFalse(extensionSessionService.authenticate(refreshed.accessToken()).isPresent());
    }

    private AppUser adminUser() {
        return appUserService.findByUsername("admin").orElseThrow();
    }

    private String reorderForStorage(List<String> values) {
        return String.join(",", values);
    }
}
