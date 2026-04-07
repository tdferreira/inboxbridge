package dev.inboxbridge.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.ImportedMessage;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.SourceImapCheckpoint;
import dev.inboxbridge.persistence.SourceImapCheckpointRepository;
import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.persistence.SourcePollingStateRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.service.destination.DestinationIdentityKeys;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
/**
 * Reconciles pre-destination-aware dedupe and checkpoint rows after an upgrade.
 *
 * <p>Older databases stored destination-scoped history using the legacy subject key
 * (for example {@code user-destination:7} or {@code gmail-destination}) because
 * destination mailbox identity keys did not exist yet. Once runtime dedupe and
 * checkpoints became mailbox-identity aware, those legacy rows would stop matching
 * the current runtime keys and the application could temporarily re-import mail or
 * ignore an existing checkpoint after upgrade.
 *
 * <p>This startup pass backfills only rows that still look legacy:
 * blank destination identities, blank checkpoint destination keys, or values that
 * still equal the old destination subject key. Already-migrated mailbox identities
 * are intentionally left untouched.
 */
public class DestinationIdentityUpgradeService {

    private static final String SYSTEM_DESTINATION_KEY = "gmail-destination";

    @Inject
    AppUserRepository appUserRepository;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    SourcePollingStateRepository sourcePollingStateRepository;

    @Inject
    SourceImapCheckpointRepository sourceImapCheckpointRepository;

    @Inject
    UserMailDestinationConfigService userMailDestinationConfigService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    EnvSourceService envSourceService;

    /**
     * Reconciles legacy rows during application startup so upgraded installs keep
     * the same effective dedupe and checkpoint continuity they had before the
     * destination-identity rollout.
     */
    void onStart(@Observes StartupEvent ignored) {
        reconcileLegacyDestinationIdentityState();
    }

    @Transactional
    /**
     * Backfills imported-message and source-checkpoint rows that still use legacy
     * destination keys instead of derived mailbox identity keys.
     */
    void reconcileLegacyDestinationIdentityState() {
        Map<String, String> identityByDestinationKey = identityByLegacyDestinationKey();
        if (identityByDestinationKey.isEmpty()) {
            return;
        }

        for (ImportedMessage message : importedMessageRepository.listAll()) {
            String expectedIdentity = identityByDestinationKey.get(message.destinationKey);
            if (expectedIdentity == null || !shouldBackfillImportedIdentity(message.destinationIdentityKey, message.destinationKey)) {
                continue;
            }
            if (shouldDropLegacyImportedMessage(message, expectedIdentity)) {
                importedMessageRepository.delete(message);
                continue;
            }
            message.destinationIdentityKey = expectedIdentity;
            importedMessageRepository.persist(message);
        }

        Map<String, String> destinationKeyBySourceId = legacyDestinationKeyBySourceId();
        if (sourceImapCheckpointRepository != null) {
            for (SourceImapCheckpoint checkpoint : sourceImapCheckpointRepository.listAll()) {
                String legacyDestinationKey = destinationKeyBySourceId.get(checkpoint.sourceId);
                String expectedIdentity = identityByDestinationKey.get(legacyDestinationKey);
                if (expectedIdentity == null || !shouldBackfillCheckpointIdentity(checkpoint.destinationKey, legacyDestinationKey)) {
                    continue;
                }
                checkpoint.destinationKey = expectedIdentity;
                sourceImapCheckpointRepository.persist(checkpoint);
            }
        }
        for (SourcePollingState state : sourcePollingStateRepository.listAll()) {
            String legacyDestinationKey = destinationKeyBySourceId.get(state.sourceId);
            String expectedIdentity = identityByDestinationKey.get(legacyDestinationKey);
            if (expectedIdentity == null) {
                continue;
            }
            if (state.imapFolderName != null && state.imapUidValidity != null && state.imapLastSeenUid != null
                    && shouldBackfillCheckpointIdentity(state.imapCheckpointDestinationKey, legacyDestinationKey)) {
                state.imapCheckpointDestinationKey = expectedIdentity;
                sourcePollingStateRepository.persist(state);
            }
            // POP checkpoints are independent from IMAP state and may need a separate
            // backfill even when the IMAP checkpoint branch above does not apply.
            if (state.popLastSeenUidl != null && !state.popLastSeenUidl.isBlank()
                    && shouldBackfillCheckpointIdentity(state.popCheckpointDestinationKey, legacyDestinationKey)) {
                state.popCheckpointDestinationKey = expectedIdentity;
                sourcePollingStateRepository.persist(state);
            }
        }
    }

    /**
     * Resolves each legacy destination subject key to the derived mailbox identity
     * key used by the current runtime. The legacy key is what older persisted rows
     * stored; the derived identity is what dedupe and checkpoints now compare.
     */
    private Map<String, String> identityByLegacyDestinationKey() {
        Map<String, String> identities = new HashMap<>();
        for (AppUser user : appUserRepository.listAll()) {
            Optional<MailDestinationTarget> target = userMailDestinationConfigService.resolveForUser(user.id, user.username);
            target.ifPresent(mailDestinationTarget ->
                    identities.put(mailDestinationTarget.subjectKey(), DestinationIdentityKeys.forTarget(mailDestinationTarget)));
        }
        systemDestinationTarget().ifPresent(target ->
                identities.put(target.subjectKey(), DestinationIdentityKeys.forTarget(target)));
        return identities;
    }

    /**
     * Maps each source id to the legacy destination key that older checkpoint rows
     * would have been associated with before destination-identity-aware state was
     * introduced.
     */
    private Map<String, String> legacyDestinationKeyBySourceId() {
        Map<String, String> destinationKeys = new HashMap<>();
        for (UserEmailAccount emailAccount : userEmailAccountRepository.listAll()) {
            destinationKeys.put(emailAccount.emailAccountId, "user-destination:" + emailAccount.userId);
            AppUser owner = appUserRepository.findByIdOptional(emailAccount.userId).orElse(null);
            if (owner == null) {
                continue;
            }
            Optional<MailDestinationTarget> target = userMailDestinationConfigService.resolveForUser(emailAccount.userId, owner.username);
            target.ifPresent(mailDestinationTarget -> destinationKeys.put(emailAccount.emailAccountId, mailDestinationTarget.subjectKey()));
        }
        envSourceService.configuredSources().forEach(indexedSource ->
                destinationKeys.put(indexedSource.source().id(), SYSTEM_DESTINATION_KEY));
        return destinationKeys;
    }

    /**
     * Resolves the env-managed Gmail destination only when all required OAuth
     * settings are present, matching the runtime rule for enabling that target.
     */
    private Optional<MailDestinationTarget> systemDestinationTarget() {
        String destinationUser = systemOAuthAppSettingsService.googleDestinationUser();
        String clientId = systemOAuthAppSettingsService.googleClientId();
        String clientSecret = systemOAuthAppSettingsService.googleClientSecret();
        String refreshToken = systemOAuthAppSettingsService.googleRefreshToken();
        String redirectUri = systemOAuthAppSettingsService.googleRedirectUri();
        if (isBlank(destinationUser) || isBlank(clientId) || isBlank(clientSecret) || isBlank(refreshToken) || isBlank(redirectUri)) {
            return Optional.empty();
        }
        return Optional.of(new GmailApiDestinationTarget(
                SYSTEM_DESTINATION_KEY,
                null,
                "system",
                UserMailDestinationConfigService.PROVIDER_GMAIL,
                destinationUser,
                clientId,
                clientSecret,
                refreshToken,
                redirectUri,
                false,
                false,
                false));
    }

    /**
     * Imported rows are safe to backfill only while they still look like legacy
     * values. Once a row has a derived mailbox identity we leave it unchanged, even
     * if the current destination later points to a different mailbox.
     */
    private boolean shouldBackfillImportedIdentity(String destinationIdentityKey, String destinationKey) {
        return destinationIdentityKey == null
                || destinationIdentityKey.isBlank()
                || destinationIdentityKey.equals(destinationKey);
    }

    /**
     * A legacy imported-message row becomes redundant when the upgrade target
     * destination identity already contains an equivalent record. In that case we
     * keep the already-modern row and drop the legacy twin instead of updating it
     * into a unique-index collision during startup.
     */
    private boolean shouldDropLegacyImportedMessage(ImportedMessage message, String expectedIdentity) {
        if (importedMessageRepository.existsBySourceMessageKey(expectedIdentity, message.sourceAccountId, message.sourceMessageKey)) {
            return true;
        }
        if (importedMessageRepository.existsByRawSha256(expectedIdentity, message.rawSha256)) {
            return true;
        }
        return message.messageIdHeader != null
                && importedMessageRepository.existsByMessageIdHeader(expectedIdentity, message.sourceAccountId, message.messageIdHeader);
    }

    /**
     * Checkpoint destination identities follow the same upgrade-only rule as
     * imported rows: backfill blanks and legacy destination keys, but preserve any
     * already-modern mailbox identity.
     */
    private boolean shouldBackfillCheckpointIdentity(String checkpointIdentityKey, String legacyDestinationKey) {
        return checkpointIdentityKey == null
                || checkpointIdentityKey.isBlank()
                || checkpointIdentityKey.equals(legacyDestinationKey);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
