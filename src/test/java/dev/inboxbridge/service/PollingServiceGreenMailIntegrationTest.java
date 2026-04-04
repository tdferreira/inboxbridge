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
import java.util.Random;

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
import dev.inboxbridge.domain.SourcePostPollAction;
import dev.inboxbridge.domain.SourcePostPollSettings;
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
    private static final Long ALICE_USER_ID = 7L;
    private static final Long BOB_USER_ID = 8L;

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

    @Test
    void runPollUsesConfiguredSourceAndDestinationFoldersInsteadOfInbox() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "inbox-message", "Should stay in the source inbox", "<inbox@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026", "project-alpha", "Should be imported from the custom source folder", "<project-alpha@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026", "project-beta", "Should also be imported from the custom source folder", "<project-beta@example.com>");

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
        service.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(runtimeAccount("Projects/2026", "Imported/Projects")));
        service.pollingSettingsService = new FixedPollingSettingsService();
        service.userPollingSettingsService = new FixedUserPollingSettingsService();
        service.sourcePollingSettingsService = new FixedSourcePollingSettingsService();
        service.sourcePollingStateService = new ReadySourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult result = service.runPoll("greenmail-integration:custom-folders");

        assertEquals(2, result.getFetched());
        assertEquals(2, result.getImported());
        assertEquals(0, result.getDuplicates());
        assertTrue(result.getErrorDetails().isEmpty());

        assertEquals(
                List.of("project-alpha", "project-beta"),
                listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, "Imported/Projects"));
        assertEquals(
                List.of(),
                listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, "INBOX"));
    }

    @Test
    void userScopedAndAllUsersPollingNeverMixMessagesAcrossUserDestinations() throws Exception {
        String aliceSourceOne = "alice-one@example.com";
        String aliceSourceTwo = "alice-two@example.com";
        String bobSourceOne = "bob-one@example.com";
        String bobSourceTwo = "bob-two@example.com";
        String sharedSourcePassword = "Source#456";
        String aliceDestination = "alice-destination@example.com";
        String bobDestination = "bob-destination@example.com";
        String sharedDestinationPassword = "Destination#456";

        sourceMail.setUser(aliceSourceOne, sharedSourcePassword);
        sourceMail.setUser(aliceSourceTwo, sharedSourcePassword);
        sourceMail.setUser(bobSourceOne, sharedSourcePassword);
        sourceMail.setUser(bobSourceTwo, sharedSourcePassword);
        destinationMail.setUser(aliceDestination, sharedDestinationPassword);
        destinationMail.setUser(bobDestination, sharedDestinationPassword);

        appendMessage(sourceMail, aliceSourceOne, sharedSourcePassword, "INBOX", "alice-one-1", "Alice source one message", "<alice-one-1@example.com>");
        appendMessage(sourceMail, aliceSourceTwo, sharedSourcePassword, "INBOX", "alice-two-1", "Alice source two message", "<alice-two-1@example.com>");
        appendMessage(sourceMail, bobSourceOne, sharedSourcePassword, "INBOX", "bob-one-1", "Bob source one message", "<bob-one-1@example.com>");
        appendMessage(sourceMail, bobSourceTwo, sharedSourcePassword, "INBOX", "bob-two-1", "Bob source two message", "<bob-two-1@example.com>");

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
        service.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(List.of(
                runtimeAccount("alice-source-one", ALICE_USER_ID, "alice", aliceSourceOne, sharedSourcePassword, aliceDestination, sharedDestinationPassword),
                runtimeAccount("alice-source-two", ALICE_USER_ID, "alice", aliceSourceTwo, sharedSourcePassword, aliceDestination, sharedDestinationPassword),
                runtimeAccount("bob-source-one", BOB_USER_ID, "bob", bobSourceOne, sharedSourcePassword, bobDestination, sharedDestinationPassword),
                runtimeAccount("bob-source-two", BOB_USER_ID, "bob", bobSourceTwo, sharedSourcePassword, bobDestination, sharedDestinationPassword)));
        service.pollingSettingsService = new FixedPollingSettingsService();
        service.userPollingSettingsService = new FixedUserPollingSettingsService();
        service.sourcePollingSettingsService = new FixedSourcePollingSettingsService();
        service.sourcePollingStateService = new ReadySourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();

        PollRunResult aliceRun = service.runPollForUser(userActor(ALICE_USER_ID), "greenmail-integration:user-alice");

        assertEquals(2, aliceRun.getFetched());
        assertEquals(2, aliceRun.getImported());
        assertEquals(0, aliceRun.getDuplicates());
        assertTrue(aliceRun.getErrorDetails().isEmpty());
        assertEquals(
                List.of("alice-one-1", "alice-two-1"),
                sortedSubjects(listSubjects(destinationMail, aliceDestination, sharedDestinationPassword, DESTINATION_FOLDER)));
        assertEquals(
                List.of(),
                listSubjectsIfFolderExists(destinationMail, bobDestination, sharedDestinationPassword, DESTINATION_FOLDER));

        PollRunResult allUsersRun = service.runPollForAllUsers(adminActor(1L), "greenmail-integration:all-users");

        assertEquals(4, allUsersRun.getFetched());
        assertEquals(2, allUsersRun.getImported());
        assertEquals(2, allUsersRun.getDuplicates());
        assertTrue(allUsersRun.getErrorDetails().isEmpty());
        assertEquals(
                List.of("alice-one-1", "alice-two-1"),
                sortedSubjects(listSubjects(destinationMail, aliceDestination, sharedDestinationPassword, DESTINATION_FOLDER)));
        assertEquals(
                List.of("bob-one-1", "bob-two-1"),
                sortedSubjects(listSubjects(destinationMail, bobDestination, sharedDestinationPassword, DESTINATION_FOLDER)));
        assertTrue(listSubjects(destinationMail, aliceDestination, sharedDestinationPassword, DESTINATION_FOLDER)
                .stream()
                .noneMatch((subject) -> subject.startsWith("bob-")));
        assertTrue(listSubjects(destinationMail, bobDestination, sharedDestinationPassword, DESTINATION_FOLDER)
                .stream()
                .noneMatch((subject) -> subject.startsWith("alice-")));
    }

    @Test
    void switchingDestinationMailboxReimportsExistingSourceMailOnlyForTheNewDestination() throws Exception {
        Random random = new Random(20260404L);
        String sharedSourcePassword = "Source#789";
        String sharedDestinationPassword = "Destination#789";
        String firstDestinationUsername = "primary-destination@example.com";
        String secondDestinationUsername = "secondary-destination@example.com";
        List<String> sourceUsernames = List.of(
                "switch-source-one@example.com",
                "switch-source-two@example.com",
                "switch-source-three@example.com");
        List<RuntimeEmailAccount> primaryDestinationAccounts = new ArrayList<>();
        List<RuntimeEmailAccount> secondaryDestinationAccounts = new ArrayList<>();
        List<String> expectedSubjects = new ArrayList<>();
        int expectedTotalMessages = 0;

        destinationMail.setUser(firstDestinationUsername, sharedDestinationPassword);
        destinationMail.setUser(secondDestinationUsername, sharedDestinationPassword);

        for (int sourceIndex = 0; sourceIndex < sourceUsernames.size(); sourceIndex += 1) {
            String sourceUsername = sourceUsernames.get(sourceIndex);
            sourceMail.setUser(sourceUsername, sharedSourcePassword);
            String sourceId = "switch-source-" + (sourceIndex + 1);
            int sourceMessageCount = 1 + random.nextInt(4);
            expectedTotalMessages += sourceMessageCount;

            for (int messageIndex = 1; messageIndex <= sourceMessageCount; messageIndex += 1) {
                String subject = sourceId + "-message-" + messageIndex;
                appendMessage(
                        sourceMail,
                        sourceUsername,
                        sharedSourcePassword,
                        "INBOX",
                        subject,
                        "Mailbox switch regression message " + messageIndex,
                        "<" + subject + "@example.com>");
                expectedSubjects.add(subject);
            }

            primaryDestinationAccounts.add(runtimeAccount(
                    sourceId,
                    ALICE_USER_ID,
                    "alice",
                    sourceUsername,
                    sharedSourcePassword,
                    "user-destination:7:primary",
                    firstDestinationUsername,
                    sharedDestinationPassword));
            secondaryDestinationAccounts.add(runtimeAccount(
                    sourceId,
                    ALICE_USER_ID,
                    "alice",
                    sourceUsername,
                    sharedSourcePassword,
                    "user-destination:7:secondary",
                    secondDestinationUsername,
                    sharedDestinationPassword));
        }

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();

        PollRunResult firstRun = pollService(primaryDestinationAccounts, importedMessageRepository)
                .runPoll("greenmail-integration:destination-switch:first");

        assertEquals(expectedTotalMessages, firstRun.getFetched());
        assertEquals(expectedTotalMessages, firstRun.getImported());
        assertEquals(0, firstRun.getDuplicates());
        assertTrue(firstRun.getErrorDetails().isEmpty());
        assertEquals(sortedSubjects(expectedSubjects), sortedSubjects(listSubjects(
                destinationMail,
                firstDestinationUsername,
                sharedDestinationPassword,
                DESTINATION_FOLDER)));
        assertEquals(expectedTotalMessages, importedMessageRepository.importedMessages.size());

        PollRunResult secondRun = pollService(primaryDestinationAccounts, importedMessageRepository)
                .runPoll("greenmail-integration:destination-switch:second");

        assertEquals(expectedTotalMessages, secondRun.getFetched());
        assertEquals(0, secondRun.getImported());
        assertEquals(expectedTotalMessages, secondRun.getDuplicates());
        assertTrue(secondRun.getErrorDetails().isEmpty());
        assertEquals(sortedSubjects(expectedSubjects), sortedSubjects(listSubjects(
                destinationMail,
                firstDestinationUsername,
                sharedDestinationPassword,
                DESTINATION_FOLDER)));
        assertEquals(expectedTotalMessages, importedMessageRepository.importedMessages.size());

        PollRunResult thirdRun = pollService(secondaryDestinationAccounts, importedMessageRepository)
                .runPoll("greenmail-integration:destination-switch:third");

        assertEquals(expectedTotalMessages, thirdRun.getFetched());
        assertEquals(expectedTotalMessages, thirdRun.getImported());
        assertEquals(0, thirdRun.getDuplicates());
        assertTrue(thirdRun.getErrorDetails().isEmpty());
        assertEquals(sortedSubjects(expectedSubjects), sortedSubjects(listSubjects(
                destinationMail,
                secondDestinationUsername,
                sharedDestinationPassword,
                DESTINATION_FOLDER)));
        assertEquals(expectedTotalMessages * 2, importedMessageRepository.importedMessages.size());

        PollRunResult fourthRun = pollService(primaryDestinationAccounts, importedMessageRepository)
                .runPoll("greenmail-integration:destination-switch:fourth");

        assertEquals(expectedTotalMessages, fourthRun.getFetched());
        assertEquals(0, fourthRun.getImported());
        assertEquals(expectedTotalMessages, fourthRun.getDuplicates());
        assertTrue(fourthRun.getErrorDetails().isEmpty());
        assertEquals(sortedSubjects(expectedSubjects), sortedSubjects(listSubjects(
                destinationMail,
                firstDestinationUsername,
                sharedDestinationPassword,
                DESTINATION_FOLDER)));
        assertEquals(sortedSubjects(expectedSubjects), sortedSubjects(listSubjects(
                destinationMail,
                secondDestinationUsername,
                sharedDestinationPassword,
                DESTINATION_FOLDER)));
        assertEquals(expectedTotalMessages * 2, importedMessageRepository.importedMessages.size());
    }

    @Test
    void runPollCanMarkSourceMessagesReadAndMoveThemAfterImport() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "alpha", "First imported message", "<alpha@example.com>");
        ensureFolderExists(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Processed");

        PollRunResult result = pollService(runtimeAccountWithPostPollSettings(new SourcePostPollSettings(
                true,
                SourcePostPollAction.MOVE,
                Optional.of("Processed"))))
                .runPoll("greenmail-integration:move-and-read");

        assertEquals(1, result.getImported());
        assertTrue(result.getErrorDetails().isEmpty());
        assertEquals(List.of("alpha"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
        assertEquals(List.of(), listSubjectsIfFolderExists(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX"));
        assertEquals(List.of("alpha"), listSubjects(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Processed"));
        assertEquals(List.of(), listUnreadSubjects(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Processed"));
    }

    @Test
    void runPollCanDeleteSourceMessagesAfterImport() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "beta", "Delete after import", "<beta@example.com>");

        PollRunResult result = pollService(runtimeAccountWithPostPollSettings(new SourcePostPollSettings(
                false,
                SourcePostPollAction.DELETE,
                Optional.empty())))
                .runPoll("greenmail-integration:delete");

        assertEquals(1, result.getImported());
        assertTrue(result.getErrorDetails().isEmpty());
        assertEquals(List.of("beta"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
        assertEquals(List.of(), listSubjectsIfFolderExists(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX"));
    }

    private PollingService pollService(RuntimeEmailAccount runtimeAccount) {
        return pollService(List.of(runtimeAccount), new RecordingImportedMessageRepository());
    }

    private PollingService pollService(List<RuntimeEmailAccount> runtimeAccounts, RecordingImportedMessageRepository importedMessageRepository) {
        PollingService service = new PollingService();
        MailSourceClient mailSourceClient = new MailSourceClient();
        mailSourceClient.mimeHashService = new MimeHashService();
        ImapAppendMailDestinationService destinationService = new ImapAppendMailDestinationService();
        ImportDeduplicationService deduplicationService = new ImportDeduplicationService();
        deduplicationService.importedMessageRepository = importedMessageRepository;
        deduplicationService.mimeHashService = new MimeHashService();

        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = deduplicationService;
        service.mailDestinationServices = new SingleMailDestinationServices(destinationService);
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(runtimeAccounts);
        service.pollingSettingsService = new FixedPollingSettingsService();
        service.userPollingSettingsService = new FixedUserPollingSettingsService();
        service.sourcePollingSettingsService = new FixedSourcePollingSettingsService();
        service.sourcePollingStateService = new ReadySourcePollingStateService();
        service.manualPollRateLimitService = new ManualPollRateLimitService();
        return service;
    }

    private RuntimeEmailAccount runtimeAccount() {
        return runtimeAccount("INBOX", DESTINATION_FOLDER);
    }

    private RuntimeEmailAccount runtimeAccount(String sourceFolder, String destinationFolder) {
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
                Optional.of(sourceFolder),
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
                        destinationFolder));
    }

    private RuntimeEmailAccount runtimeAccount(
            String sourceId,
            Long userId,
            String ownerUsername,
            String sourceUsername,
            String sourcePassword,
            String destinationUsername,
            String destinationPassword) {
        return runtimeAccount(
                sourceId,
                userId,
                ownerUsername,
                sourceUsername,
                sourcePassword,
                "user-destination:" + userId,
                destinationUsername,
                destinationPassword);
    }

    private RuntimeEmailAccount runtimeAccount(
            String sourceId,
            Long userId,
            String ownerUsername,
            String sourceUsername,
            String sourcePassword,
            String destinationKey,
            String destinationUsername,
            String destinationPassword) {
        return new RuntimeEmailAccount(
                sourceId,
                "USER",
                userId,
                ownerUsername,
                true,
                InboxBridgeConfig.Protocol.IMAP,
                "127.0.0.1",
                sourceMail.getImap().getPort(),
                false,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                sourceUsername,
                sourcePassword,
                "",
                Optional.of("INBOX"),
                false,
                Optional.of("Imported/Test"),
                new ImapAppendDestinationTarget(
                        destinationKey,
                        userId,
                        ownerUsername,
                        UserMailDestinationConfigService.PROVIDER_CUSTOM,
                        "127.0.0.1",
                        destinationMail.getImap().getPort(),
                        false,
                        InboxBridgeConfig.AuthMethod.PASSWORD,
                        InboxBridgeConfig.OAuthProvider.NONE,
                        destinationUsername,
                        destinationPassword,
                        DESTINATION_FOLDER));
    }

    private RuntimeEmailAccount runtimeAccountWithPostPollSettings(SourcePostPollSettings postPollSettings) {
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
                postPollSettings,
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

    private static void ensureFolderExists(GreenMail greenMail, String username, String password, String folderName) throws Exception {
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
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private static List<String> listSubjectsIfFolderExists(GreenMail greenMail, String username, String password, String folderName) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        Folder folder = null;
        try {
            store.connect("127.0.0.1", greenMail.getImap().getPort(), username, password);
            folder = store.getFolder(folderName);
            if (!folder.exists()) {
                return List.of();
            }
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

    private static List<String> listUnreadSubjects(GreenMail greenMail, String username, String password, String folderName) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        Folder folder = null;
        try {
            store.connect("127.0.0.1", greenMail.getImap().getPort(), username, password);
            folder = store.getFolder(folderName);
            if (!folder.exists()) {
                return List.of();
            }
            folder.open(Folder.READ_ONLY);
            Message[] messages = folder.search(new jakarta.mail.search.FlagTerm(new jakarta.mail.Flags(jakarta.mail.Flags.Flag.SEEN), false));
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

    private static List<String> sortedSubjects(List<String> subjects) {
        return subjects.stream().sorted().toList();
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

        @Override
        public List<RuntimeEmailAccount> listEnabledForUser(dev.inboxbridge.persistence.AppUser actor) {
            return emailAccounts.stream()
                    .filter(emailAccount -> actor != null && actor.id != null && actor.id.equals(emailAccount.ownerUserId()))
                    .toList();
        }
    }

    private static final class FixedPollingSettingsService extends PollingSettingsService {
        @Override
        public EffectivePollingSettings effectiveSettings() {
            return new EffectivePollingSettings(true, "5m", Duration.ofMinutes(5), 10);
        }

        @Override
        public ManualPollRateLimit effectiveManualPollRateLimit() {
            return new ManualPollRateLimit(5, Duration.ofMinutes(1), 60);
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
        public void record(String sourceId, String trigger, Instant startedAt, Instant finishedAt, int fetched, int imported, long importedBytes, int duplicates, int spamJunkMessageCount, String actorUsername, String executionSurface, String error) {
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

    private static dev.inboxbridge.persistence.AppUser userActor(Long userId) {
        dev.inboxbridge.persistence.AppUser actor = new dev.inboxbridge.persistence.AppUser();
        actor.id = userId;
        actor.role = dev.inboxbridge.persistence.AppUser.Role.USER;
        return actor;
    }

    private static dev.inboxbridge.persistence.AppUser adminActor(Long userId) {
        dev.inboxbridge.persistence.AppUser actor = new dev.inboxbridge.persistence.AppUser();
        actor.id = userId;
        actor.role = dev.inboxbridge.persistence.AppUser.Role.ADMIN;
        return actor;
    }
}
