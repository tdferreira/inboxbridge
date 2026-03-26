package dev.inboxbridge.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.dto.AdminBridgeSummary;
import dev.inboxbridge.dto.AdminDashboardResponse;
import dev.inboxbridge.dto.AdminDestinationSummary;
import dev.inboxbridge.dto.AdminOverallSummary;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AdminDashboardService {

    @Inject
    BridgeConfig config;

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    SourcePollEventService sourcePollEventService;

    public AdminDashboardResponse dashboard() {
        Map<String, ImportStats> importStatsBySource = new HashMap<>();
        for (Object[] row : importedMessageRepository.summarizeBySource()) {
            importStatsBySource.put(
                    (String) row[0],
                    new ImportStats(((Long) row[1]).longValue(), (Instant) row[2]));
        }

        List<AdminBridgeSummary> bridges = config.sources().stream()
                .map(source -> {
                    ImportStats importStats = importStatsBySource.getOrDefault(source.id(), ImportStats.EMPTY);
                    AdminPollEventSummary lastEvent = sourcePollEventService.latestForSource(source.id()).orElse(null);
                    return new AdminBridgeSummary(
                            source.id(),
                            source.enabled(),
                            source.protocol().name(),
                            source.authMethod().name(),
                            source.oauthProvider().name(),
                            source.host(),
                            source.port(),
                            source.tls(),
                            source.folder().orElse("INBOX"),
                            source.unreadOnly(),
                            source.customLabel().orElse(""),
                            passwordConfigured(source.password()),
                            tokenStorageMode(source),
                            importStats.totalImported(),
                            importStats.lastImportedAt(),
                            lastEvent);
                })
                .toList();

        int sourcesWithErrors = (int) bridges.stream()
                .filter(bridge -> bridge.lastEvent() != null && "ERROR".equals(bridge.lastEvent().status()))
                .count();

        return new AdminDashboardResponse(
                new AdminOverallSummary(
                        config.sources().size(),
                        (int) config.sources().stream().filter(BridgeConfig.Source::enabled).count(),
                        importedMessageRepository.count(),
                        sourcesWithErrors,
                        config.pollEnabled()),
                new AdminDestinationSummary(
                        gmailClientConfigured(),
                        googleTokenStorageMode(),
                        config.gmail().createMissingLabels(),
                        config.gmail().neverMarkSpam(),
                        config.gmail().processForCalendar()),
                bridges,
                sourcePollEventService.recentEvents(20));
    }

    private boolean gmailClientConfigured() {
        return !config.gmail().clientId().isBlank()
                && !"replace-me".equals(config.gmail().clientId())
                && !config.gmail().clientSecret().isBlank()
                && !"replace-me".equals(config.gmail().clientSecret());
    }

    private String googleTokenStorageMode() {
        if (oAuthCredentialService.secureStorageConfigured() && oAuthCredentialService.findGoogleCredential().isPresent()) {
            return "DATABASE";
        }
        if (!config.gmail().refreshToken().isBlank() && !"replace-me".equals(config.gmail().refreshToken())) {
            return "ENVIRONMENT";
        }
        return oAuthCredentialService.secureStorageConfigured() ? "CONFIGURED_BUT_EMPTY" : "NOT_CONFIGURED";
    }

    private String tokenStorageMode(BridgeConfig.Source source) {
        if (source.authMethod() == BridgeConfig.AuthMethod.PASSWORD) {
            return "PASSWORD";
        }
        if (source.oauthProvider() != BridgeConfig.OAuthProvider.MICROSOFT) {
            return "UNKNOWN";
        }
        if (oAuthCredentialService.secureStorageConfigured() && oAuthCredentialService.findMicrosoftCredential(source.id()).isPresent()) {
            return "DATABASE";
        }
        if (source.oauthRefreshToken().isPresent() && !source.oauthRefreshToken().orElse("").isBlank()) {
            return "ENVIRONMENT";
        }
        return oAuthCredentialService.secureStorageConfigured() ? "CONFIGURED_BUT_EMPTY" : "NOT_CONFIGURED";
    }

    private boolean passwordConfigured(String password) {
        return !password.isBlank() && !"replace-me".equals(password);
    }

    private record ImportStats(long totalImported, Instant lastImportedAt) {
        private static final ImportStats EMPTY = new ImportStats(0, null);
    }
}
