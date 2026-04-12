package dev.inboxbridge.service.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollSettings;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.PollLiveSourceView;
import dev.inboxbridge.dto.PollLiveView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.service.polling.PollingLiveService;
import dev.inboxbridge.service.polling.SourcePollEventService;
import dev.inboxbridge.service.user.RuntimeEmailAccountService;
import dev.inboxbridge.service.user.UserUiPreferenceService;

class ExtensionStatusServiceTest {

    @Test
    void statusUsesPersistedErrorsAndOverlaysLiveRunningState() {
        ExtensionStatusService service = new ExtensionStatusService();
        service.runtimeEmailAccountService = new RuntimeEmailAccountService() {
            @Override
            public List<RuntimeEmailAccount> listEnabledForUser(AppUser actor) {
                return List.of(runtimeAccount("alpha"), runtimeAccount("beta"));
            }
        };
        service.sourcePollEventService = new SourcePollEventService() {
            @Override
            public Optional<AdminPollEventSummary> latestForSource(String sourceId) {
                if ("alpha".equals(sourceId)) {
                    return Optional.of(new AdminPollEventSummary(
                            sourceId,
                            "user-ui",
                            "ERROR",
                            Instant.parse("2026-04-12T15:00:00Z"),
                            Instant.parse("2026-04-12T15:01:00Z"),
                            3,
                            0,
                            0L,
                            0,
                            0,
                            "alice",
                            "my-inboxbridge",
                            "Authentication failed for source alpha",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));
                }
                return Optional.of(new AdminPollEventSummary(
                        sourceId,
                        "user-ui",
                        "SUCCESS",
                        Instant.parse("2026-04-12T15:00:00Z"),
                        Instant.parse("2026-04-12T15:01:00Z"),
                        9,
                        4,
                        100L,
                        5,
                        0,
                        "alice",
                        "my-inboxbridge",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
            }
        };
        service.pollingLiveService = new PollingLiveService() {
            @Override
            public PollLiveView snapshotFor(AppUser viewer) {
                return new PollLiveView(
                        true,
                        "run-1",
                        "RUNNING",
                        "extension-user",
                        viewer.username,
                        true,
                        "beta",
                        Instant.parse("2026-04-12T15:02:00Z"),
                        Instant.parse("2026-04-12T15:03:00Z"),
                        List.of(new PollLiveSourceView(
                                "beta",
                                viewer.username,
                                "beta",
                                "RUNNING",
                                true,
                                1,
                                1,
                                0,
                                0,
                                0L,
                                0L,
                                0,
                                0,
                                0,
                                null,
                                Instant.parse("2026-04-12T15:02:00Z"),
                                null)));
            }
        };
        service.userUiPreferenceService = new UserUiPreferenceService() {
            @Override
            public java.util.Optional<dev.inboxbridge.dto.UserUiPreferenceView> viewForUser(Long userId) {
                return java.util.Optional.of(defaultView());
            }

            @Override
            public dev.inboxbridge.dto.UserUiPreferenceView defaultView() {
                return new dev.inboxbridge.dto.UserUiPreferenceView(
                        false, false, false, false, false, false, false, false, false, false, false, false, false,
                        false, false, false, List.of(), List.of(), "pt-PT", "DARK_BLUE", "AUTO", "AUTO", "", List.of());
            }
        };

        AppUser user = new AppUser();
        user.id = 3L;
        user.username = "alice";

        var status = service.statusForUser(user);

        assertTrue(status.poll().running());
        assertEquals("pt-PT", status.user().language());
        assertEquals("DARK_BLUE", status.user().themeMode());
        assertEquals(2, status.summary().sourceCount());
        assertEquals(2, status.summary().enabledSourceCount());
        assertEquals(1, status.summary().errorSourceCount());
        assertEquals(12, status.summary().lastCompletedRun().fetched());
        assertEquals(4, status.summary().lastCompletedRun().imported());
        assertEquals(5, status.summary().lastCompletedRun().duplicates());
        assertEquals(1, status.summary().lastCompletedRun().errors());
        assertEquals("ERROR", status.sources().getFirst().status());
        assertTrue(status.sources().getFirst().needsAttention());
        assertEquals("RUNNING", status.sources().get(1).status());
    }

    private RuntimeEmailAccount runtimeAccount(String id) {
        return new RuntimeEmailAccount(
                id,
                "USER",
                3L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "",
                java.util.Optional.of("INBOX"),
                false,
                SourceFetchMode.POLLING,
                java.util.Optional.empty(),
                SourcePostPollSettings.none(),
                null);
    }
}
