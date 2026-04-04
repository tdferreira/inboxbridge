package dev.inboxbridge.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import dev.inboxbridge.persistence.RemoteSessionRepository;
import dev.inboxbridge.persistence.UserSessionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SessionLocationAlertService {

    private static final int RECENT_SESSION_LIMIT = 12;
    private static final int MIN_PRIOR_LOCATIONS_FOR_WARNING = 3;

    @Inject
    UserSessionRepository userSessionRepository;

    @Inject
    RemoteSessionRepository remoteSessionRepository;

    public SessionLocationAssessment assessNewSession(Long userId, String sessionType, Long sessionId, String locationLabel) {
        String normalizedType = normalizeSessionType(sessionType);
        List<SessionLocationSnapshot> recentSessions = recentSessions(userId);
        Map<String, Boolean> unusualBySession = assessSnapshots(recentSessions);
        String sessionKey = sessionKey(normalizedType, sessionId);
        return new SessionLocationAssessment(
                normalizeLocation(locationLabel),
                Boolean.TRUE.equals(unusualBySession.get(sessionKey)));
    }

    public Map<String, Boolean> assessSnapshots(List<SessionLocationSnapshot> sessions) {
        List<SessionLocationSnapshot> orderedSessions = sessions == null
                ? List.of()
                : sessions.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(SessionLocationSnapshot::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();

        Map<String, Boolean> unusualBySession = new LinkedHashMap<>();
        for (int index = 0; index < orderedSessions.size(); index += 1) {
            SessionLocationSnapshot current = orderedSessions.get(index);
            boolean unusual = isUnusualLocation(current, orderedSessions.subList(index + 1, orderedSessions.size()));
            unusualBySession.put(sessionKey(current.sessionType(), current.sessionId()), unusual);
        }
        return unusualBySession;
    }

    private List<SessionLocationSnapshot> recentSessions(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return Stream.concat(
                        userSessionRepository.listRecentByUserId(userId, RECENT_SESSION_LIMIT).stream()
                                .map(session -> new SessionLocationSnapshot("BROWSER", session.id, session.createdAt, session.locationLabel)),
                        remoteSessionRepository.listRecentByUserId(userId, RECENT_SESSION_LIMIT).stream()
                                .map(session -> new SessionLocationSnapshot("REMOTE", session.id, session.createdAt, session.locationLabel)))
                .sorted(Comparator.comparing(SessionLocationSnapshot::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_SESSION_LIMIT)
                .toList();
    }

    private boolean isUnusualLocation(SessionLocationSnapshot current, List<SessionLocationSnapshot> olderSessions) {
        String currentLocation = normalizeLocation(current == null ? null : current.locationLabel());
        if (currentLocation == null) {
            return false;
        }

        List<String> priorLocations = olderSessions == null
                ? List.of()
                : olderSessions.stream()
                        .map(SessionLocationSnapshot::locationLabel)
                        .map(this::normalizeLocation)
                        .filter(Objects::nonNull)
                        .toList();
        if (priorLocations.size() < MIN_PRIOR_LOCATIONS_FOR_WARNING) {
            return false;
        }
        if (priorLocations.stream().anyMatch(currentLocation::equals)) {
            return false;
        }

        String currentCountry = extractCountryToken(currentLocation);
        if (currentCountry == null) {
            return false;
        }
        return priorLocations.stream()
                .map(this::extractCountryToken)
                .filter(Objects::nonNull)
                .noneMatch(currentCountry::equals);
    }

    private String normalizeSessionType(String value) {
        String normalized = value == null ? "BROWSER" : value.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? "BROWSER" : normalized;
    }

    private String sessionKey(String sessionType, Long sessionId) {
        return normalizeSessionType(sessionType) + ":" + String.valueOf(sessionId);
    }

    private String normalizeLocation(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private String extractCountryToken(String locationLabel) {
        String normalized = normalizeLocation(locationLabel);
        if (normalized == null) {
            return null;
        }
        int commaIndex = normalized.lastIndexOf(',');
        String token = commaIndex >= 0 ? normalized.substring(commaIndex + 1) : normalized;
        String trimmed = token.trim().toUpperCase(Locale.ROOT);
        return trimmed.isBlank() ? null : trimmed;
    }

    public record SessionLocationSnapshot(
            String sessionType,
            Long sessionId,
            Instant createdAt,
            String locationLabel) {
    }

    public record SessionLocationAssessment(
            String locationLabel,
            boolean unusualLocation) {
    }
}
