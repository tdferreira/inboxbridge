package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.RemoteSession;
import dev.inboxbridge.persistence.RemoteSessionRepository;
import dev.inboxbridge.persistence.UserSession;
import dev.inboxbridge.persistence.UserSessionRepository;

class SessionLocationAlertServiceTest {

    @Test
    void flagsLocationsInNewCountriesOnceRecentHistoryIsStable() {
        SessionLocationAlertService service = new SessionLocationAlertService();
        service.userSessionRepository = new FakeUserSessionRepository(List.of(
                browserSession(41L, Instant.parse("2026-04-04T10:00:00Z"), "Lisbon, PT"),
                browserSession(40L, Instant.parse("2026-04-04T09:00:00Z"), "Porto, PT"),
                browserSession(39L, Instant.parse("2026-04-04T08:00:00Z"), "Braga, PT"),
                browserSession(38L, Instant.parse("2026-04-04T07:00:00Z"), "Berlin, DE")));
        service.remoteSessionRepository = new FakeRemoteSessionRepository(List.of());

        SessionLocationAlertService.SessionLocationAssessment assessment =
                service.assessNewSession(7L, "BROWSER", 41L, "Lisbon, PT");

        assertFalse(assessment.unusualLocation());

        SessionLocationAlertService.SessionLocationAssessment unusualAssessment =
                service.assessSnapshots(List.of(
                        new SessionLocationAlertService.SessionLocationSnapshot("REMOTE", 55L, Instant.parse("2026-04-04T11:00:00Z"), "Berlin, DE"),
                        new SessionLocationAlertService.SessionLocationSnapshot("BROWSER", 41L, Instant.parse("2026-04-04T10:00:00Z"), "Lisbon, PT"),
                        new SessionLocationAlertService.SessionLocationSnapshot("BROWSER", 40L, Instant.parse("2026-04-04T09:00:00Z"), "Porto, PT"),
                        new SessionLocationAlertService.SessionLocationSnapshot("BROWSER", 39L, Instant.parse("2026-04-04T08:00:00Z"), "Braga, PT")))
                        .entrySet()
                        .stream()
                        .filter(entry -> "REMOTE:55".equals(entry.getKey()))
                        .map(entry -> new SessionLocationAlertService.SessionLocationAssessment("Berlin, DE", entry.getValue()))
                        .findFirst()
                        .orElseThrow();

        assertTrue(unusualAssessment.unusualLocation());
    }

    @Test
    void doesNotFlagUnusualLocationsWithoutEnoughHistory() {
        SessionLocationAlertService service = new SessionLocationAlertService();
        service.userSessionRepository = new FakeUserSessionRepository(List.of(
                browserSession(11L, Instant.parse("2026-04-04T10:00:00Z"), "Berlin, DE"),
                browserSession(10L, Instant.parse("2026-04-04T09:00:00Z"), "Lisbon, PT")));
        service.remoteSessionRepository = new FakeRemoteSessionRepository(List.of());

        SessionLocationAlertService.SessionLocationAssessment assessment =
                service.assessNewSession(7L, "BROWSER", 11L, "Berlin, DE");

        assertEquals("Berlin, DE", assessment.locationLabel());
        assertFalse(assessment.unusualLocation());
    }

    private static UserSession browserSession(Long id, Instant createdAt, String locationLabel) {
        UserSession session = new UserSession();
        session.id = id;
        session.createdAt = createdAt;
        session.locationLabel = locationLabel;
        return session;
    }

    private static final class FakeUserSessionRepository extends UserSessionRepository {
        private final List<UserSession> sessions;

        private FakeUserSessionRepository(List<UserSession> sessions) {
            this.sessions = sessions;
        }

        @Override
        public List<UserSession> listRecentByUserId(Long userId, int limit) {
            return sessions;
        }
    }

    private static final class FakeRemoteSessionRepository extends RemoteSessionRepository {
        private final List<RemoteSession> sessions;

        private FakeRemoteSessionRepository(List<RemoteSession> sessions) {
            this.sessions = sessions;
        }

        @Override
        public List<RemoteSession> listRecentByUserId(Long userId, int limit) {
            return sessions;
        }
    }
}
