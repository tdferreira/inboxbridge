package dev.inboxbridge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourcePostPollAction;
import dev.inboxbridge.domain.SourcePostPollSettings;
import dev.inboxbridge.dto.AdminPollEventSummary;
import dev.inboxbridge.dto.PollRunError;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.dto.RemoteControlView;
import dev.inboxbridge.dto.RemoteSessionUserResponse;
import dev.inboxbridge.dto.RemoteSourceView;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

@ApplicationScoped
public class RemoteControlService {
    private static final String DEFAULT_LANGUAGE = "en";

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    EnvSourceService envSourceService;

    @Inject
    AppUserService appUserService;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    UserEmailAccountService userEmailAccountService;

    @Inject
    UserMailDestinationConfigService userMailDestinationConfigService;

    @Inject
    RuntimeEmailAccountService runtimeEmailAccountService;

    @Inject
    SourcePollingSettingsService sourcePollingSettingsService;

    @Inject
    SourcePollingStateService sourcePollingStateService;

    @Inject
    SourcePollEventService sourcePollEventService;

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    PollingService pollingService;

    @Inject
    RemotePollRateLimitService remotePollRateLimitService;

    @Inject
    UserUiPreferenceService userUiPreferenceService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    public RemoteControlView viewFor(AppUser actor) {
        dev.inboxbridge.dto.UserUiPreferenceView uiPreference = userUiPreferenceService.viewForUser(actor.id).orElse(userUiPreferenceService.defaultView());
        boolean hasOwnSourceEmailAccounts = userEmailAccountRepository.count("userId = ?1 and enabled = true", actor.id) > 0;
        boolean hasReadyDestinationMailbox = userMailDestinationConfigService.resolveForUser(actor.id, actor.username).isPresent();
        List<RemoteSourceView> sources = listSources(actor);
        return new RemoteControlView(
                new RemoteSessionUserResponse(
                        actor.id,
                        null,
                        actor.username,
                        actor.role.name(),
                        true,
                        actor.role == AppUser.Role.ADMIN,
                        systemOAuthAppSettingsService.effectiveMultiUserEnabled(),
                        false,
                        uiPreference.language(),
                        uiPreference.dateFormat(),
                        uiPreference.timezoneMode(),
                        uiPreference.timezone()),
                sources,
                hasOwnSourceEmailAccounts,
                hasReadyDestinationMailbox,
                !hasOwnSourceEmailAccounts || !hasReadyDestinationMailbox,
                inboxBridgeConfig.security().remote().pollRateLimitWindow().toString(),
                inboxBridgeConfig.security().remote().pollRateLimitCount());
    }

    public PollRunResult runUserPoll(AppUser actor, String actorKey) {
        PollRunResult limited = remoteRateLimit(actorKey, null);
        if (limited != null) {
            return limited;
        }
        return pollingService.runPollForUser(actor, "remote-ui");
    }

    public PollRunResult runAllUsersPoll(AppUser actor, String actorKey) {
        if (actor.role != AppUser.Role.ADMIN) {
            throw new ForbiddenException("Admin access required");
        }
        PollRunResult limited = remoteRateLimit(actorKey, null);
        if (limited != null) {
            return limited;
        }
        return pollingService.runPollForAllUsers(actor, "remote-admin");
    }

    public PollRunResult runSourcePoll(AppUser actor, String sourceId, String actorKey) {
        RuntimeEmailAccount emailAccount = resolveSource(actor, sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mail fetcher id"));
        PollRunResult limited = remoteRateLimit(actorKey, sourceId);
        if (limited != null) {
            return limited;
        }
        return pollingService.runPollForSource(emailAccount, "remote-source", actor, actor.role + ":" + actor.id);
    }

    public List<RemoteSourceView> listSources(AppUser actor) {
        Map<String, ImportStats> importStatsBySource = importStatsBySource();
        List<RemoteSourceView> sources = new ArrayList<>();

        if (actor.role == AppUser.Role.ADMIN) {
            for (EnvSourceService.IndexedSource indexedSource : envSourceService.configuredSources()) {
                InboxBridgeConfig.Source source = indexedSource.source();
                if (!source.enabled()) {
                    continue;
                }
                PollingSettingsService.EffectivePollingSettings effectiveSettings = sourcePollingSettingsService.effectiveSettingsFor(
                        runtimeEmailAccountService.findSystemBridge(source.id()).orElseGet(() -> new RuntimeEmailAccount(
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
                                source.fetchMode(),
                                source.customLabel(),
                                SourcePostPollSettings.none(),
                                null)));
                ImportStats importStats = importStatsBySource.getOrDefault(source.id(), ImportStats.EMPTY);
                sources.add(new RemoteSourceView(
                        source.id(),
                        "SYSTEM",
                        null,
                        "System",
                        source.enabled(),
                        effectiveSettings.pollEnabled(),
                        effectiveSettings.pollIntervalText(),
                        effectiveSettings.fetchWindow(),
                        source.protocol().name(),
                        source.host(),
                        source.port(),
                        source.username(),
                        source.folder().orElse("INBOX"),
                        source.unreadOnly(),
                        source.customLabel().orElse(""),
                        false,
                        "NONE",
                        "",
                        importStats.totalImported(),
                        importStats.lastImportedAt(),
                        sourcePollEventService.latestForSource(source.id()).orElse(null),
                        sourcePollingStateService.viewForSource(source.id()).orElse(null)));
            }
        }

        List<UserEmailAccount> userAccounts = actor.role == AppUser.Role.ADMIN
                ? userEmailAccountRepository.list("order by userId asc, emailAccountId asc")
                : userEmailAccountRepository.list("userId", actor.id);
        for (UserEmailAccount emailAccount : userAccounts) {
            if (!emailAccount.enabled) {
                continue;
            }
            AppUser owner = appUserService.findById(emailAccount.userId).orElse(null);
            if (owner == null || !owner.active || !owner.approved) {
                continue;
            }
            Optional<RuntimeEmailAccount> runtime = actor.role == AppUser.Role.ADMIN
                    ? runtimeEmailAccountService.findUserManagedById(emailAccount.emailAccountId)
                    : runtimeEmailAccountService.findAccessibleForUser(actor, emailAccount.emailAccountId);
            if (runtime.isEmpty()) {
                continue;
            }
            PollingSettingsService.EffectivePollingSettings effectiveSettings = sourcePollingSettingsService.effectiveSettingsFor(runtime.get());
            ImportStats importStats = importStatsBySource.getOrDefault(emailAccount.emailAccountId, ImportStats.EMPTY);
            sources.add(new RemoteSourceView(
                    emailAccount.emailAccountId,
                    "USER",
                    owner.id,
                    owner.username,
                    emailAccount.enabled,
                    effectiveSettings.pollEnabled(),
                    effectiveSettings.pollIntervalText(),
                    effectiveSettings.fetchWindow(),
                    emailAccount.protocol.name(),
                    emailAccount.host,
                    emailAccount.port,
                    emailAccount.username,
                    emailAccount.folderName == null ? "INBOX" : emailAccount.folderName,
                    emailAccount.unreadOnly,
                    emailAccount.customLabel == null ? "" : emailAccount.customLabel,
                    emailAccount.markReadAfterPoll,
                    (emailAccount.postPollAction == null ? SourcePostPollAction.NONE : emailAccount.postPollAction).name(),
                    emailAccount.postPollTargetFolder == null ? "" : emailAccount.postPollTargetFolder,
                    importStats.totalImported(),
                    importStats.lastImportedAt(),
                    sourcePollEventService.latestForSource(emailAccount.emailAccountId).orElse(null),
                    sourcePollingStateService.viewForSource(emailAccount.emailAccountId).orElse(null)));
        }

        return sources.stream()
                .sorted((left, right) -> (left.ownerLabel() + ":" + left.sourceId()).compareToIgnoreCase(right.ownerLabel() + ":" + right.sourceId()))
                .toList();
    }

    private Optional<RuntimeEmailAccount> resolveSource(AppUser actor, String sourceId) {
        if (actor.role == AppUser.Role.ADMIN) {
            return runtimeEmailAccountService.findAnyAccessibleForAdmin(sourceId);
        }
        return runtimeEmailAccountService.findAccessibleForUser(actor, sourceId);
    }

    private PollRunResult remoteRateLimit(String actorKey, String sourceId) {
        RemotePollRateLimitService.Decision decision = remotePollRateLimitService.tryAcquire(
                actorKey,
                inboxBridgeConfig.security().remote().pollRateLimitCount(),
                inboxBridgeConfig.security().remote().pollRateLimitWindow(),
                Instant.now());
        if (decision.allowed()) {
            return null;
        }
        PollRunResult result = new PollRunResult();
        result.addError(new PollRunError(
                "remote_poll_rate_limited",
                sourceId,
                "Remote polling is temporarily rate limited until "
                        + Optional.ofNullable(decision.retryAt()).map(String::valueOf).orElse("a later retry time") + ".",
                Optional.ofNullable(decision.retryAt()).map(String::valueOf).orElse(null)));
        result.finish();
        return result;
    }

    private Map<String, ImportStats> importStatsBySource() {
        Map<String, ImportStats> importStatsBySource = new HashMap<>();
        for (Object[] row : importedMessageRepository.summarizeBySource()) {
            importStatsBySource.put(
                    (String) row[0],
                    new ImportStats(((Long) row[1]).longValue(), (Instant) row[2]));
        }
        return importStatsBySource;
    }

    private record ImportStats(long totalImported, Instant lastImportedAt) {
        private static final ImportStats EMPTY = new ImportStats(0, null);
    }
}
