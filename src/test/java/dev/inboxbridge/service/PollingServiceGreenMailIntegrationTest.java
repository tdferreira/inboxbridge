package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.persistence.ImportedMessage;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class PollingServiceGreenMailIntegrationTest {

    private static final String SOURCE_USERNAME = "source@example.com";
    private static final String SOURCE_PASSWORD = "Source#123";
    private static final String DESTINATION_USERNAME = "destination@example.com";
    private static final String DESTINATION_PASSWORD = "Destination#123";
    private static final String DESTINATION_FOLDER = "Imported";

    private GreenMail sourceMail;
    private GreenMail destinationMail;

    @BeforeEach
    void setUp() {
        sourceMail = new GreenMail(new ServerSetup(freePort(), null, ServerSetup.PROTOCOL_IMAP));
        sourceMail.start();
        sourceMail.setUser(SOURCE_USERNAME, SOURCE_PASSWORD);

        destinationMail = new GreenMail(new ServerSetup(freePort(), null, ServerSetup.PROTOCOL_IMAP));
        destinationMail.start();
        destinationMail.setUser(DESTINATION_USERNAME, DESTINATION_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        if (sourceMail != null) {
            sourceMail.stop();
        }
        if (destinationMail != null) {
            destinationMail.stop();
        }
    }

    @Test
    void runPollFetchesFromSourceAndAppendsIntoDestinationWithoutDuplicatingMessages() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "alpha", "First imported message", "<alpha@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "beta", "Second imported message", "<beta@example.com>");

        PollingService service = new PollingService();
        MailSourceClient mailSourceClient = new MailSourceClient();
        mailSourceClient.mimeHashService = new MimeHashService();
        ImapAppendMailDestinationService destinationService = new ImapAppendMailDestinationService();
        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        ImportDeduplicationService deduplicationService = new ImportDeduplicationService();
        deduplicationService.importedMessageRepository = importedMessageRepository;
        deduplicationService.mimeHashService = new MimeHashService();

        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = deduplicationService;
        service.mailDestinationServices = new SingleMailDestinationServices(destinationService);
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount()));
        service.pollingSettingsService = new FixedPollingSettingsService();
        service.userPollingSettingsService = new FixedUserPollingSettingsService();
        service.sourcePollingSettingsService = new FixedSourcePollingSettingsService();
        service.sourcePollingStateService = new ReadySourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult firstRun = service.runPoll("greenmail-integration:first");

        assertEquals(2, firstRun.getFetched());
        assertEquals(2, firstRun.getImported());
        assertEquals(0, firstRun.getDuplicates());
        assertTrue(firstRun.getErrorDetails().isEmpty());
        assertEquals(2, importedMessageRepository.importedMessages.size());

        List<String> destinationSubjectsAfterFirstRun = listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER);
        assertEquals(List.of("alpha", "beta"), destinationSubjectsAfterFirstRun);

        PollRunResult secondRun = service.runPoll("greenmail-integration:second");

        assertEquals(2, secondRun.getFetched());
        assertEquals(0, secondRun.getImported());
        assertEquals(2, secondRun.getDuplicates());
        assertTrue(secondRun.getErrorDetails().isEmpty());
        assertEquals(2, importedMessageRepository.importedMessages.size());

        List<String> destinationSubjectsAfterSecondRun = listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER);
        assertEquals(List.of("alpha", "beta"), destinationSubjectsAfterSecondRun);
    }

    private RuntimeEmailAccount runtimeAccount() {
        return new RuntimeEmailAccount(
                "greenmail-source",
                "USER",
                7L,
                "alice",
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "127.0.0.1",
                sourceMail.getImap().getPort(),
                false,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                SOURCE_USERNAME,
                SOURCE_PASSWORD,
                "",
                Optional.of("INBOX"),
                false,
                Optional.of("Imported/Test"),
                new ImapAppendDestinationTarget(
                        "user-destination:7",
                        7L,
                        "alice",
                        UserMailDestinationConfigService.PROVIDER_CUSTOM,
                        "127.0.0.1",
                        destinationMail.getImap().getPort(),
                        false,
                        InboxBridgeConfig.AuthMethod.PASSWORD,
                        InboxBridgeConfig.OAuthProvider.NONE,
                        DESTINATION_USERNAME,
                        DESTINATION_PASSWORD,
                        DESTINATION_FOLDER));
    }

    private static void appendMessage(
            GreenMail greenMail,
            String username,
            String password,
            String folderName,
            String subject,
            String body,
            String messageId) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        Folder folder = null;
        try {
            store.connect("127.0.0.1", greenMail.getImap().getPort(), username, password);
            folder = store.getFolder(folderName);
            if (!folder.exists()) {
                folder.create(Folder.HOLDS_MESSAGES);
            }
            if (!folder.isOpen()) {
                folder.open(Folder.READ_WRITE);
            }
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("sender@example.com"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(username));
            message.setSubject(subject, StandardCharsets.UTF_8.name());
            message.setText(body, StandardCharsets.UTF_8.name());
            message.setHeader("Message-ID", messageId);
            message.setSentDate(java.util.Date.from(Instant.now()));
            message.saveChanges();
            folder.appendMessages(new Message[] { message });
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private static List<String> listSubjects(GreenMail greenMail, String username, String password, String folderName) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        Folder folder = null;
        try {
            store.connect("127.0.0.1", greenMail.getImap().getPort(), username, password);
            folder = store.getFolder(folderName);
            assertTrue(folder.exists(), "Expected destination folder to exist after import");
            folder.open(Folder.READ_ONLY);
            Message[] messages = folder.getMessages();
            List<String> subjects = new ArrayList<>();
            for (Message message : messages) {
                subjects.add(message.getSubject());
            }
            return subjects;
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private static void closeQuietly(Folder folder) {
        if (folder == null) {
            return;
        }
        try {
            if (folder.isOpen()) {
                folder.close(false);
            }
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(Store store) {
        if (store == null) {
            return;
        }
        try {
            if (store.isConnected()) {
                store.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to allocate a free TCP port for GreenMail", e);
        }
    }

    private static final class RecordingImportedMessageRepository extends ImportedMessageRepository {
        private final List<ImportedMessage> importedMessages = new ArrayList<>();

        @Override
        public boolean existsBySourceMessageKey(String destinationKey, String sourceAccountId, String sourceMessageKey) {
            return importedMessages.stream().anyMatch((message) ->
                    destinationKey.equals(message.destinationKey)
                            && sourceAccountId.equals(message.sourceAccountId)
                            && sourceMessageKey.equals(message.sourceMessageKey));
        }

        @Override
        public boolean existsByRawSha256(String destinationKey, String rawSha256) {
            return importedMessages.stream().anyMatch((message) ->
                    destinationKey.equals(message.destinationKey)
                            && rawSha256.equals(message.rawSha256));
        }

        @Override
        public void persist(ImportedMessage entity) {
            importedMessages.add(entity);
        }
    }

    private static final class FixedRuntimeEmailAccountService extends RuntimeEmailAccountService {
        private final List<RuntimeEmailAccount> emailAccounts;

        private FixedRuntimeEmailAccountService(List<RuntimeEmailAccount> emailAccounts) {
            this.emailAccounts = emailAccounts;
        }

        @Override
        public List<RuntimeEmailAccount> listEnabledForPolling() {
            return emailAccounts;
        }
    }

    private static final class FixedPollingSettingsService extends PollingSettingsService {
        @Override
        public EffectivePollingSettings effectiveSettings() {
            return new EffectivePollingSettings(true, "5m", Duration.ofMinutes(5), 10);
        }
    }

    private static final class FixedUserPollingSettingsService extends UserPollingSettingsService {
        @Override
        public PollingSettingsService.EffectivePollingSettings effectiveSettingsForUser(Long userId) {
            return new PollingSettingsService.EffectivePollingSettings(true, "5m", Duration.ofMinutes(5), 10);
        }
    }

    private static final class FixedSourcePollingSettingsService extends SourcePollingSettingsService {
        @Override
        public PollingSettingsService.EffectivePollingSettings effectiveSettingsFor(RuntimeEmailAccount bridge) {
            return new PollingSettingsService.EffectivePollingSettings(true, "5m", Duration.ofMinutes(5), 10);
        }
    }

    private static final class ReadySourcePollingStateService extends SourcePollingStateService {
        @Override
        public PollEligibility eligibility(String sourceId, PollingSettingsService.EffectivePollingSettings settings, Instant now, boolean ignoreInterval, boolean ignoreCooldown) {
            return new PollEligibility(true, "READY", null);
        }

        @Override
        public PollEligibility eligibility(String sourceId, PollingSettingsService.EffectivePollingSettings settings, Instant now, boolean ignoreInterval) {
            return new PollEligibility(true, "READY", null);
        }

        @Override
        public void recordSuccess(String sourceId, Instant finishedAt, PollingSettingsService.EffectivePollingSettings settings) {
        }

        @Override
        public void recordFailure(String sourceId, Instant finishedAt, String failureReason) {
        }
    }

    private static final class NoopSourcePollEventService extends SourcePollEventService {
        @Override
        public void record(String sourceId, String trigger, Instant startedAt, Instant finishedAt, int fetched, int imported, int duplicates, String error) {
        }
    }

    private static final class SingleMailDestinationServices implements Instance<MailDestinationService> {
        private final MailDestinationService service;

        private SingleMailDestinationServices(MailDestinationService service) {
            this.service = service;
        }

        @Override
        public MailDestinationService get() {
            return service;
        }

        @Override
        public Iterator<MailDestinationService> iterator() {
            return List.of(service).iterator();
        }

        @Override
        public Instance<MailDestinationService> select(java.lang.annotation.Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends MailDestinationService> Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends MailDestinationService> Instance<U> select(TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUnsatisfied() {
            return false;
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public void destroy(MailDestinationService instance) {
        }

        @Override
        public Handle<MailDestinationService> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Handle<MailDestinationService>> handles() {
            throw new UnsupportedOperationException();
        }
    }
}
