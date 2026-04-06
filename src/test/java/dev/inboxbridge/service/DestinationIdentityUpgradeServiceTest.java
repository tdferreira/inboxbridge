package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.ImportedMessage;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.persistence.SourcePollingStateRepository;
import dev.inboxbridge.persistence.UserEmailAccount;
import dev.inboxbridge.persistence.UserEmailAccountRepository;

class DestinationIdentityUpgradeServiceTest {

    @Test
    void upgradeBackfillsLegacyUserDestinationImportedMessagesAndCheckpoints() {
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";

        UserEmailAccount source = new UserEmailAccount();
        source.userId = 7L;
        source.emailAccountId = "source-1";

        ImportedMessage importedMessage = new ImportedMessage();
        importedMessage.destinationKey = "user-destination:7";
        importedMessage.destinationIdentityKey = "user-destination:7";

        SourcePollingState pollingState = new SourcePollingState();
        pollingState.sourceId = "source-1";
        pollingState.imapFolderName = "INBOX";
        pollingState.imapUidValidity = 44L;
        pollingState.imapLastSeenUid = 10L;
        pollingState.popLastSeenUidl = "uidl-1";

        ImapAppendDestinationTarget currentTarget = new ImapAppendDestinationTarget(
                "user-destination:7",
                7L,
                "alice",
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "Imported");

        DestinationIdentityUpgradeService service = new DestinationIdentityUpgradeService();
        service.appUserRepository = new FakeAppUserRepository(List.of(user));
        service.userEmailAccountRepository = new FakeUserEmailAccountRepository(List.of(source));
        service.importedMessageRepository = new FakeImportedMessageRepository(List.of(importedMessage));
        service.sourcePollingStateRepository = new FakeSourcePollingStateRepository(List.of(pollingState));
        service.userMailDestinationConfigService = new FakeUserMailDestinationConfigService(Map.of(7L, currentTarget));
        service.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService();
        service.envSourceService = new FakeEnvSourceService(List.of());

        service.reconcileLegacyDestinationIdentityState();

        String expectedIdentity = DestinationIdentityKeys.forTarget(currentTarget);
        assertEquals(expectedIdentity, importedMessage.destinationIdentityKey);
        assertEquals(expectedIdentity, pollingState.imapCheckpointDestinationKey);
        assertEquals(expectedIdentity, pollingState.popCheckpointDestinationKey);
    }

    @Test
    void upgradeLeavesAlreadyDestinationAwareRowsUntouched() {
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";

        UserEmailAccount source = new UserEmailAccount();
        source.userId = 7L;
        source.emailAccountId = "source-1";

        ImportedMessage importedMessage = new ImportedMessage();
        importedMessage.destinationKey = "user-destination:7";
        importedMessage.destinationIdentityKey = "imap-append:already-modern";

        SourcePollingState pollingState = new SourcePollingState();
        pollingState.sourceId = "source-1";
        pollingState.imapFolderName = "INBOX";
        pollingState.imapUidValidity = 44L;
        pollingState.imapLastSeenUid = 10L;
        pollingState.imapCheckpointDestinationKey = "imap-append:older-mailbox";

        ImapAppendDestinationTarget currentTarget = new ImapAppendDestinationTarget(
                "user-destination:7",
                7L,
                "alice",
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "Imported");

        DestinationIdentityUpgradeService service = new DestinationIdentityUpgradeService();
        service.appUserRepository = new FakeAppUserRepository(List.of(user));
        service.userEmailAccountRepository = new FakeUserEmailAccountRepository(List.of(source));
        service.importedMessageRepository = new FakeImportedMessageRepository(List.of(importedMessage));
        service.sourcePollingStateRepository = new FakeSourcePollingStateRepository(List.of(pollingState));
        service.userMailDestinationConfigService = new FakeUserMailDestinationConfigService(Map.of(7L, currentTarget));
        service.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService();
        service.envSourceService = new FakeEnvSourceService(List.of());

        service.reconcileLegacyDestinationIdentityState();

        assertEquals("imap-append:already-modern", importedMessage.destinationIdentityKey);
        assertEquals("imap-append:older-mailbox", pollingState.imapCheckpointDestinationKey);
    }

    @Test
    void upgradeDropsLegacyImportedMessageWhenModernSourceKeyDuplicateAlreadyExists() {
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";

        ImportedMessage legacy = new ImportedMessage();
        legacy.id = 1L;
        legacy.destinationKey = "user-destination:7";
        legacy.destinationIdentityKey = "user-destination:7";
        legacy.sourceAccountId = "source-1";
        legacy.sourceMessageKey = "source-1:imap-folder:INBOX:44:10";
        legacy.rawSha256 = "sha-1";

        ImportedMessage modern = new ImportedMessage();
        modern.id = 2L;
        modern.destinationKey = "user-destination:7";
        modern.destinationIdentityKey = "imap-append:modern";
        modern.sourceAccountId = "source-1";
        modern.sourceMessageKey = "source-1:imap-folder:INBOX:44:10";
        modern.rawSha256 = "sha-1";

        ImapAppendDestinationTarget currentTarget = new ImapAppendDestinationTarget(
                "user-destination:7",
                7L,
                "alice",
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "Imported");
        String expectedIdentity = DestinationIdentityKeys.forTarget(currentTarget);

        FakeImportedMessageRepository importedRepository = new FakeImportedMessageRepository(new ArrayList<>(List.of(legacy, modern)));
        modern.destinationIdentityKey = expectedIdentity;

        DestinationIdentityUpgradeService service = new DestinationIdentityUpgradeService();
        service.appUserRepository = new FakeAppUserRepository(List.of(user));
        service.userEmailAccountRepository = new FakeUserEmailAccountRepository(List.of());
        service.importedMessageRepository = importedRepository;
        service.sourcePollingStateRepository = new FakeSourcePollingStateRepository(List.of());
        service.userMailDestinationConfigService = new FakeUserMailDestinationConfigService(Map.of(7L, currentTarget));
        service.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService();
        service.envSourceService = new FakeEnvSourceService(List.of());

        service.reconcileLegacyDestinationIdentityState();

        assertFalse(importedRepository.importedMessages.contains(legacy));
        assertTrue(importedRepository.importedMessages.contains(modern));
    }

    @Test
    void upgradeDropsLegacyImportedMessageWhenModernRawShaDuplicateAlreadyExists() {
        AppUser user = new AppUser();
        user.id = 7L;
        user.username = "alice";

        ImportedMessage legacy = new ImportedMessage();
        legacy.id = 1L;
        legacy.destinationKey = "user-destination:7";
        legacy.destinationIdentityKey = "user-destination:7";
        legacy.sourceAccountId = "source-1";
        legacy.sourceMessageKey = "legacy-key";
        legacy.rawSha256 = "sha-1";

        ImportedMessage modern = new ImportedMessage();
        modern.id = 2L;
        modern.destinationKey = "user-destination:7";
        modern.destinationIdentityKey = "imap-append:modern";
        modern.sourceAccountId = "source-1";
        modern.sourceMessageKey = "modern-key";
        modern.rawSha256 = "sha-1";

        ImapAppendDestinationTarget currentTarget = new ImapAppendDestinationTarget(
                "user-destination:7",
                7L,
                "alice",
                UserMailDestinationConfigService.PROVIDER_CUSTOM,
                "imap.example.com",
                993,
                true,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                "alice@example.com",
                "secret",
                "Imported");
        String expectedIdentity = DestinationIdentityKeys.forTarget(currentTarget);

        FakeImportedMessageRepository importedRepository = new FakeImportedMessageRepository(new ArrayList<>(List.of(legacy, modern)));
        modern.destinationIdentityKey = expectedIdentity;

        DestinationIdentityUpgradeService service = new DestinationIdentityUpgradeService();
        service.appUserRepository = new FakeAppUserRepository(List.of(user));
        service.userEmailAccountRepository = new FakeUserEmailAccountRepository(List.of());
        service.importedMessageRepository = importedRepository;
        service.sourcePollingStateRepository = new FakeSourcePollingStateRepository(List.of());
        service.userMailDestinationConfigService = new FakeUserMailDestinationConfigService(Map.of(7L, currentTarget));
        service.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService();
        service.envSourceService = new FakeEnvSourceService(List.of());

        service.reconcileLegacyDestinationIdentityState();

        assertFalse(importedRepository.importedMessages.contains(legacy));
        assertTrue(importedRepository.importedMessages.contains(modern));
    }

    @Test
    void upgradeBackfillsLegacySystemGmailDestinationRows() {
        ImportedMessage importedMessage = new ImportedMessage();
        importedMessage.destinationKey = "gmail-destination";
        importedMessage.destinationIdentityKey = "gmail-destination";

        SourcePollingState pollingState = new SourcePollingState();
        pollingState.sourceId = "env-source";
        pollingState.popLastSeenUidl = "uidl-1";

        DestinationIdentityUpgradeService service = new DestinationIdentityUpgradeService();
        service.appUserRepository = new FakeAppUserRepository(List.of());
        service.userEmailAccountRepository = new FakeUserEmailAccountRepository(List.of());
        service.importedMessageRepository = new FakeImportedMessageRepository(List.of(importedMessage));
        service.sourcePollingStateRepository = new FakeSourcePollingStateRepository(List.of(pollingState));
        service.userMailDestinationConfigService = new FakeUserMailDestinationConfigService(Map.of());
        service.systemOAuthAppSettingsService = new FakeSystemOAuthAppSettingsService(
                "dest@example.com",
                "client-id",
                "client-secret",
                "refresh-token",
                "https://localhost/callback");
        service.envSourceService = new FakeEnvSourceService(List.of(new EnvSourceService.IndexedSource(0, new FakeSource("env-source"))));

        service.reconcileLegacyDestinationIdentityState();

        MailDestinationTarget systemTarget = new GmailApiDestinationTarget(
                "gmail-destination",
                null,
                "system",
                UserMailDestinationConfigService.PROVIDER_GMAIL,
                "dest@example.com",
                "client-id",
                "client-secret",
                "refresh-token",
                "https://localhost/callback",
                false,
                false,
                false);
        String expectedIdentity = DestinationIdentityKeys.forTarget(systemTarget);
        assertEquals(expectedIdentity, importedMessage.destinationIdentityKey);
        assertEquals(expectedIdentity, pollingState.popCheckpointDestinationKey);
    }

    private static final class FakeAppUserRepository extends AppUserRepository {
        private final List<AppUser> users;

        private FakeAppUserRepository(List<AppUser> users) {
            this.users = users;
        }

        @Override
        public List<AppUser> listAll() {
            return users;
        }

        @Override
        public Optional<AppUser> findByIdOptional(Long id) {
            return users.stream().filter(user -> id.equals(user.id)).findFirst();
        }
    }

    private static final class FakeUserEmailAccountRepository extends UserEmailAccountRepository {
        private final List<UserEmailAccount> emailAccounts;

        private FakeUserEmailAccountRepository(List<UserEmailAccount> emailAccounts) {
            this.emailAccounts = emailAccounts;
        }

        @Override
        public List<UserEmailAccount> listAll() {
            return emailAccounts;
        }
    }

    private static final class FakeImportedMessageRepository extends ImportedMessageRepository {
        private final List<ImportedMessage> importedMessages;

        private FakeImportedMessageRepository(List<ImportedMessage> importedMessages) {
            this.importedMessages = importedMessages;
        }

        @Override
        public List<ImportedMessage> listAll() {
            return importedMessages;
        }

        @Override
        public void persist(ImportedMessage entity) {
        }

        @Override
        public boolean existsBySourceMessageKey(String destinationIdentityKey, String sourceAccountId, String sourceMessageKey) {
            return importedMessages.stream().anyMatch(message ->
                    destinationIdentityKey.equals(message.destinationIdentityKey)
                            && sourceAccountId.equals(message.sourceAccountId)
                            && sourceMessageKey.equals(message.sourceMessageKey));
        }

        @Override
        public boolean existsByRawSha256(String destinationIdentityKey, String rawSha256) {
            return importedMessages.stream().anyMatch(message ->
                    destinationIdentityKey.equals(message.destinationIdentityKey)
                            && rawSha256.equals(message.rawSha256));
        }

        @Override
        public boolean existsByMessageIdHeader(String destinationIdentityKey, String sourceAccountId, String messageIdHeader) {
            return importedMessages.stream().anyMatch(message ->
                    destinationIdentityKey.equals(message.destinationIdentityKey)
                            && sourceAccountId.equals(message.sourceAccountId)
                            && messageIdHeader.equals(message.messageIdHeader));
        }

        @Override
        public void delete(ImportedMessage entity) {
            importedMessages.remove(entity);
        }
    }

    private static final class FakeSourcePollingStateRepository extends SourcePollingStateRepository {
        private final List<SourcePollingState> pollingStates;

        private FakeSourcePollingStateRepository(List<SourcePollingState> pollingStates) {
            this.pollingStates = pollingStates;
        }

        @Override
        public List<SourcePollingState> listAll() {
            return pollingStates;
        }

        @Override
        public void persist(SourcePollingState entity) {
        }
    }

    private static final class FakeUserMailDestinationConfigService extends UserMailDestinationConfigService {
        private final Map<Long, MailDestinationTarget> targets;

        private FakeUserMailDestinationConfigService(Map<Long, MailDestinationTarget> targets) {
            this.targets = targets;
        }

        @Override
        public Optional<MailDestinationTarget> resolveForUser(Long userId, String ownerUsername) {
            return Optional.ofNullable(targets.get(userId));
        }
    }

    private static final class FakeSystemOAuthAppSettingsService extends SystemOAuthAppSettingsService {
        private final String destinationUser;
        private final String clientId;
        private final String clientSecret;
        private final String refreshToken;
        private final String redirectUri;

        private FakeSystemOAuthAppSettingsService() {
            this("", "", "", "", "");
        }

        private FakeSystemOAuthAppSettingsService(
                String destinationUser,
                String clientId,
                String clientSecret,
                String refreshToken,
                String redirectUri) {
            this.destinationUser = destinationUser;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.refreshToken = refreshToken;
            this.redirectUri = redirectUri;
        }

        @Override
        public String googleDestinationUser() {
            return destinationUser;
        }

        @Override
        public String googleClientId() {
            return clientId;
        }

        @Override
        public String googleClientSecret() {
            return clientSecret;
        }

        @Override
        public String googleRefreshToken() {
            return refreshToken;
        }

        @Override
        public String googleRedirectUri() {
            return redirectUri;
        }
    }

    private static final class FakeEnvSourceService extends EnvSourceService {
        private final List<IndexedSource> sources;

        private FakeEnvSourceService(List<IndexedSource> sources) {
            this.sources = sources;
        }

        @Override
        public List<IndexedSource> configuredSources() {
            return sources;
        }
    }

    private record FakeSource(String id) implements InboxBridgeConfig.Source {
        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public InboxBridgeConfig.Protocol protocol() {
            return InboxBridgeConfig.Protocol.IMAP;
        }

        @Override
        public String host() {
            return "imap.example.com";
        }

        @Override
        public int port() {
            return 993;
        }

        @Override
        public boolean tls() {
            return true;
        }

        @Override
        public InboxBridgeConfig.AuthMethod authMethod() {
            return InboxBridgeConfig.AuthMethod.PASSWORD;
        }

        @Override
        public InboxBridgeConfig.OAuthProvider oauthProvider() {
            return InboxBridgeConfig.OAuthProvider.NONE;
        }

        @Override
        public String username() {
            return "user@example.com";
        }

        @Override
        public String password() {
            return "secret";
        }

        @Override
        public Optional<String> oauthRefreshToken() {
            return Optional.empty();
        }

        @Override
        public Optional<String> folder() {
            return Optional.of("INBOX");
        }

        @Override
        public boolean unreadOnly() {
            return false;
        }

        @Override
        public dev.inboxbridge.domain.SourceFetchMode fetchMode() {
            return dev.inboxbridge.domain.SourceFetchMode.POLLING;
        }

        @Override
        public Optional<String> customLabel() {
            return Optional.of("Imported/Test");
        }
    }
}
