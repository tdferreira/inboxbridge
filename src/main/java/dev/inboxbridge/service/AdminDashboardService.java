package dev.inboxbridge.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.AdminEmailAccountSummary;
import dev.inboxbridge.dto.AdminDashboardResponse;
import dev.inboxbridge.dto.AdminDestinationSummary;
import dev.inboxbridge.dto.AdminOverallSummary;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.GlobalPollingStatsView;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AdminDashboardService {

    @Inject
    InboxBridgeConfig config;

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    SourcePollEventService sourcePollEventService;

    @Inject
    PollingSettingsService pollingSettingsService;

    @Inject
    EnvSourceService envSourceService;

    @Inject
    SourcePollingStateService sourcePollingStateService;

    @Inject
    SourcePollingSettingsService sourcePollingSettingsService;

    @Inject
    PollingStatsService pollingStatsService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    public AdminDashboardResponse dashboard() {
        PollingSettingsService.EffectivePollingSettings effectivePolling = pollingSettingsService.effectiveSettings();
        Map<String, ImportStats> importStatsBySource = new HashMap<>();
        for (Object[] row : importedMessageRepository.summarizeBySource()) {
            importStatsBySource.put(
                    (String) row[0],
                    new ImportStats(((Long) row[1]).longValue(), (Instant) row[2]));
        }

        List<EnvSourceService.IndexedSource> configuredSources = envSourceService.configuredSources();
        Map<String, dev.inboxbridge.dto.SourcePollingStateView> pollingStateBySource = sourcePollingStateService.viewBySourceIds(
                configuredSources.stream()
                        .map(indexedSource -> indexedSource.source().id())
                        .toList());

        List<AdminEmailAccountSummary> emailAccounts = configuredSources.stream()
                .map(indexedSource -> {
                    InboxBridgeConfig.Source source = indexedSource.source();
                    ImportStats importStats = importStatsBySource.getOrDefault(source.id(), ImportStats.EMPTY);
                    OAuthCredentialService.StoredOAuthCredential microsoftCredential = microsoftCredential(source);
                    AdminPollEventSummary lastEvent = sanitizeLastEvent(
                            sourcePollEventService.latestForSource(source.id()).orElse(null),
                            microsoftCredential);
                    PollingSettingsService.EffectivePollingSettings effectiveSourcePolling = sourcePollingSettingsService.effectiveSettingsFor(
                            new dev.inboxbridge.domain.RuntimeEmailAccount(
                                    source.id(),
                                    "SYSTEM",
                                    null,
                                    "system",
                                    source.enabled(),
                                    source.protocol(),
                                    source.host(),
                                    source.port(),
                                    source.tls(),
                                    source.authMethod(),
                                    source.oauthProvider(),
                                    source.username(),
                                    source.password(),
                                    source.oauthRefreshToken().orElse(""),
                                    source.folder(),
                                    source.unreadOnly(),
                                    source.customLabel(),
                                    null));
                    return new AdminEmailAccountSummary(
                            source.id(),
                            source.enabled(),
                            effectiveSourcePolling.pollEnabled(),
                            effectiveSourcePolling.pollIntervalText(),
                            effectiveSourcePolling.fetchWindow(),
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
                            tokenStorageMode(source, microsoftCredential),
                            importStats.totalImported(),
                            importStats.lastImportedAt(),
                            lastEvent,
                            pollingStateBySource.get(source.id()));
                })
                .toList();

        int sourcesWithErrors = (int) emailAccounts.stream()
                .filter(emailAccount -> emailAccount.lastEvent() != null && "ERROR".equals(emailAccount.lastEvent().status()))
                .count();

        GlobalPollingStatsView stats = pollingStatsService.globalStats(sourcesWithErrors);

        return new AdminDashboardResponse(
                new AdminOverallSummary(
                        stats.configuredMailFetchers(),
                        stats.enabledMailFetchers(),
                        stats.totalImportedMessages(),
                        sourcesWithErrors,
                        effectivePolling.pollEnabled(),
                        effectivePolling.pollIntervalText(),
                        effectivePolling.fetchWindow()),
                stats,
                pollingSettingsService.view(),
                new AdminDestinationSummary(
                        gmailClientConfigured(),
                        googleTokenStorageMode(),
                        config.gmail().createMissingLabels(),
                        config.gmail().neverMarkSpam(),
                        config.gmail().processForCalendar()),
                emailAccounts,
                sourcePollEventService.recentEvents(20));
    }

    private boolean gmailClientConfigured() {
        return systemOAuthAppSettingsService.googleClientConfigured();
    }

    private String googleTokenStorageMode() {
        if (oAuthCredentialService.secureStorageConfigured() && oAuthCredentialService.findGoogleCredential().isPresent()) {
            return "DATABASE";
        }
        if (!systemOAuthAppSettingsService.googleRefreshToken().isBlank() && !"replace-me".equals(systemOAuthAppSettingsService.googleRefreshToken())) {
            return "ENVIRONMENT";
        }
        return oAuthCredentialService.secureStorageConfigured() ? "CONFIGURED_BUT_EMPTY" : "NOT_CONFIGURED";
    }

    private String tokenStorageMode(InboxBridgeConfig.Source source, OAuthCredentialService.StoredOAuthCredential oauthCredential) {
        if (source.authMethod() == InboxBridgeConfig.AuthMethod.PASSWORD) {
            return "PASSWORD";
        }
        if (oauthCredential != null) {
            return "DATABASE";
        }
        if (source.oauthRefreshToken().isPresent() && !source.oauthRefreshToken().orElse("").isBlank()) {
            return "ENVIRONMENT";
        }
        return oAuthCredentialService.secureStorageConfigured() ? "CONFIGURED_BUT_EMPTY" : "NOT_CONFIGURED";
    }

    private OAuthCredentialService.StoredOAuthCredential microsoftCredential(InboxBridgeConfig.Source source) {
        if (!oAuthCredentialService.secureStorageConfigured()) {
            return null;
        }
        return switch (source.oauthProvider()) {
            case MICROSOFT -> oAuthCredentialService.findMicrosoftCredential(source.id()).orElse(null);
            case GOOGLE -> oAuthCredentialService.findGoogleCredential("source-google:" + source.id()).orElse(null);
            default -> null;
        };
    }

    private AdminPollEventSummary sanitizeLastEvent(
            AdminPollEventSummary lastEvent,
            OAuthCredentialService.StoredOAuthCredential microsoftCredential) {
        if (lastEvent == null || microsoftCredential == null) {
            return lastEvent;
        }
        if (!"ERROR".equals(lastEvent.status())) {
            return lastEvent;
        }
        String errorMessage = lastEvent.error();
        if (errorMessage == null || !errorMessage.contains("configured for OAuth2 but has no refresh token")) {
            return lastEvent;
        }
        if (lastEvent.finishedAt() == null || microsoftCredential.updatedAt() == null) {
            return lastEvent;
        }
        if (microsoftCredential.updatedAt().isAfter(lastEvent.finishedAt())) {
            return null;
        }
        return lastEvent;
    }

    private boolean passwordConfigured(String password) {
        return !password.isBlank() && !"replace-me".equals(password);
    }

    private record ImportStats(long totalImported, Instant lastImportedAt) {
        private static final ImportStats EMPTY = new ImportStats(0, null);
    }
}
