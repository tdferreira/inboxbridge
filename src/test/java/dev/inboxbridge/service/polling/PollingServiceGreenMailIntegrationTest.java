package dev.inboxbridge.service.polling;

import dev.inboxbridge.service.destination.*;

import dev.inboxbridge.service.*;
import dev.inboxbridge.service.mail.MailSourceClient;
import dev.inboxbridge.service.mail.MailSourceClient.MailboxCountProbe;
import dev.inboxbridge.service.mail.MailSourceStandaloneFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.eclipse.angus.mail.pop3.POP3Folder;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.FetchedMessage;
import dev.inboxbridge.domain.ImapAppendDestinationTarget;
import dev.inboxbridge.domain.MailDestinationTarget;
import dev.inboxbridge.domain.RuntimeEmailAccount;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollAction;
import dev.inboxbridge.domain.SourcePostPollSettings;
import dev.inboxbridge.dto.MailImportResponse;
import dev.inboxbridge.dto.PollRunResult;
import dev.inboxbridge.persistence.ImportedMessage;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.SourceImapCheckpoint;
import dev.inboxbridge.persistence.SourceImapCheckpointRepository;
import dev.inboxbridge.persistence.SourcePollingState;
import dev.inboxbridge.persistence.SourcePollingStateRepository;
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

    @RegisterExtension
    final GreenMailExtension sourceMail = new GreenMailExtension(new ServerSetup[] {
            new ServerSetup(0, null, ServerSetup.PROTOCOL_IMAP),
            new ServerSetup(0, null, ServerSetup.PROTOCOL_POP3)
    });

    @RegisterExtension
    final GreenMailExtension destinationMail = new GreenMailExtension(new ServerSetup(0, null, ServerSetup.PROTOCOL_IMAP));

    @BeforeEach
    void setUp() {
        sourceMail.setUser(SOURCE_USERNAME, SOURCE_PASSWORD);
        destinationMail.setUser(DESTINATION_USERNAME, DESTINATION_PASSWORD);
    }

    @Test
    void listFoldersReturnsInboxFirstAndIncludesNestedSourceFolders() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026", "project-alpha", "Stored in nested folder", "<folders-alpha@example.com>");

        MailSourceClient client = standaloneMailSourceClient();

        List<String> folders = client.listFolders(runtimeAccount());

        assertEquals(List.of("INBOX", "Projects/2026"), folders);
    }

    @Test
    void probeSpamOrJunkFolderFindsLocalizedSpamFolderAndCountsMessages() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Correo no deseado", "spam-alpha", "Localized spam folder message", "<spam-alpha@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Correo no deseado", "spam-beta", "Second localized spam folder message", "<spam-beta@example.com>");

        MailSourceClient client = standaloneMailSourceClient();

        Optional<MailSourceClient.MailboxCountProbe> probe = client.probeSpamOrJunkFolder(runtimeAccount());

        assertTrue(probe.isPresent());
        assertEquals("Correo no deseado", probe.get().folderName());
        assertEquals(2, probe.get().messageCount());
    }

    @Test
    void pop3PollingUsesPersistedUidlCheckpointToResumeOnlyNewMail() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "alpha", "First imported message", "<alpha-pop@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "beta", "Second imported message", "<beta-pop@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        InMemoryReadyCheckpointSourcePollingStateService sourcePollingStateService = new InMemoryReadyCheckpointSourcePollingStateService();
        PollingService service = pollService(List.of(runtimePop3Account()), importedMessageRepository, sourcePollingStateService);

        PollRunResult firstRun = service.runPoll("greenmail-integration:pop3:first");

        assertEquals(2, firstRun.getFetched());
        assertEquals(2, firstRun.getImported());
        assertEquals(0, firstRun.getDuplicates());
        assertTrue(firstRun.getErrorDetails().isEmpty());
        String checkpointAfterFirstRun = sourcePollingStateService.popCheckpoint(
                "greenmail-source-pop",
                DestinationIdentityKeys.forTarget(runtimePop3Account().destination())).orElseThrow();
        List<String> uidlsAfterFirstRun = listPopUidls(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD);
        assertEquals(List.of("alpha", "beta"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));

        PollRunResult secondRun = service.runPoll("greenmail-integration:pop3:second");
        List<String> uidlsAfterSecondRun = listPopUidls(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD);

        assertEquals(uidlsAfterFirstRun, uidlsAfterSecondRun, "GreenMail POP UIDLs changed across reconnects for checkpoint " + checkpointAfterFirstRun);
        assertEquals(0, secondRun.getFetched(), "Expected POP checkpoint " + checkpointAfterFirstRun + " to suppress refetch when UIDLs are stable");
        assertEquals(0, secondRun.getImported());
        assertEquals(0, secondRun.getDuplicates());
        assertTrue(secondRun.getErrorDetails().isEmpty());
        assertEquals(
                checkpointAfterFirstRun,
                sourcePollingStateService.popCheckpoint(
                        "greenmail-source-pop",
                        DestinationIdentityKeys.forTarget(runtimePop3Account().destination())).orElseThrow());
        assertEquals(List.of("alpha", "beta"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));

        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "gamma", "Third imported message", "<gamma-pop@example.com>");

        PollRunResult thirdRun = service.runPoll("greenmail-integration:pop3:third");

        assertEquals(1, thirdRun.getFetched());
        assertEquals(1, thirdRun.getImported());
        assertEquals(0, thirdRun.getDuplicates());
        assertTrue(thirdRun.getErrorDetails().isEmpty());
        assertEquals(List.of("alpha", "beta", "gamma"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
        assertTrue(!checkpointAfterFirstRun.equals(sourcePollingStateService.popCheckpoint(
                "greenmail-source-pop",
                DestinationIdentityKeys.forTarget(runtimePop3Account().destination())).orElseThrow()));
    }

    @Test
    void pop3CheckpointDoesNotCarryAcrossDestinationMailboxSwitches() throws Exception {
        String secondDestinationUsername = "destination-two@example.com";
        destinationMail.setUser(secondDestinationUsername, DESTINATION_PASSWORD);
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "alpha", "First imported message", "<alpha-pop-switch@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        InMemoryReadyCheckpointSourcePollingStateService sourcePollingStateService = new InMemoryReadyCheckpointSourcePollingStateService();

        PollRunResult firstRun = pollService(
                List.of(runtimePop3Account("user-destination:7", DESTINATION_USERNAME, DESTINATION_FOLDER)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:pop3-destination-switch:first");

        assertEquals(1, firstRun.getFetched());
        assertEquals(1, firstRun.getImported());
        assertEquals(List.of("alpha"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));

        PollRunResult secondRun = pollService(
                List.of(runtimePop3Account("user-destination:7", secondDestinationUsername, DESTINATION_FOLDER)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:pop3-destination-switch:second");

        assertEquals(1, secondRun.getFetched());
        assertEquals(1, secondRun.getImported());
        assertEquals(0, secondRun.getDuplicates());
        assertEquals(List.of("alpha"), listSubjects(destinationMail, secondDestinationUsername, DESTINATION_PASSWORD, DESTINATION_FOLDER));
    }

    @Test
    void imapCheckpointDoesNotCarryAcrossDestinationMailboxSwitches() throws Exception {
        String secondDestinationUsername = "imap-destination-two@example.com";
        destinationMail.setUser(secondDestinationUsername, DESTINATION_PASSWORD);
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "alpha", "First imported message", "<alpha-imap-switch@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        InMemoryReadyCheckpointSourcePollingStateService sourcePollingStateService = new InMemoryReadyCheckpointSourcePollingStateService();

        PollRunResult firstRun = pollService(
                List.of(runtimeAccount("greenmail-source", 7L, "alice", SOURCE_USERNAME, SOURCE_PASSWORD, "user-destination:7", DESTINATION_USERNAME, DESTINATION_PASSWORD)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-destination-switch:first");

        assertEquals(1, firstRun.getFetched());
        assertEquals(1, firstRun.getImported());
        assertEquals(List.of("alpha"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));

        PollRunResult secondRun = pollService(
                List.of(runtimeAccount("greenmail-source", 7L, "alice", SOURCE_USERNAME, SOURCE_PASSWORD, "user-destination:7", secondDestinationUsername, DESTINATION_PASSWORD)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-destination-switch:second");

        assertEquals(1, secondRun.getFetched());
        assertEquals(1, secondRun.getImported());
        assertEquals(0, secondRun.getDuplicates());
        assertEquals(List.of("alpha"), listSubjects(destinationMail, secondDestinationUsername, DESTINATION_PASSWORD, DESTINATION_FOLDER));
    }

    @Test
    void imapMessageIdDedupeSuppressesReimportWhenUidAndMimeChange() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "alpha", "First imported message", "<stable-imap@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "beta", "Second imported message variant", "<stable-imap@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        PollRunResult result = pollService(
                List.of(runtimeAccount()),
                importedMessageRepository,
                new ReadySourcePollingStateService())
                .runPoll("greenmail-integration:imap-message-id-dedupe");

        assertEquals(2, result.getFetched());
        assertEquals(1, result.getImported());
        assertEquals(1, result.getDuplicates());
        assertEquals(List.of("alpha"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
    }

    @Test
    void pop3MessageIdDedupeSuppressesReimportWhenUidlAndMimeChange() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "alpha", "First POP imported message", "<stable-pop@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "beta", "Second POP imported message variant", "<stable-pop@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        InMemoryReadyCheckpointSourcePollingStateService sourcePollingStateService = new InMemoryReadyCheckpointSourcePollingStateService();

        PollRunResult result = pollService(
                List.of(runtimePop3Account()),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:pop3-message-id-dedupe");

        assertEquals(2, result.getFetched());
        assertEquals(1, result.getImported());
        assertEquals(1, result.getDuplicates());
        assertEquals(List.of("alpha"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
    }

    @Test
    void runPollFetchesFromSourceAndAppendsIntoDestinationWithoutDuplicatingMessages() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "alpha", "First imported message", "<alpha@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "beta", "Second imported message", "<beta@example.com>");

        PollingService service = new PollingService();
        MailSourceClient mailSourceClient = standaloneMailSourceClient();
        ImapAppendMailDestinationService destinationService = new ImapAppendMailDestinationService();
        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        ImportDeduplicationService deduplicationService = new ImportDeduplicationService(
                importedMessageRepository,
                new MimeHashService());

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
        MailSourceClient mailSourceClient = standaloneMailSourceClient();
        ImapAppendMailDestinationService destinationService = new ImapAppendMailDestinationService();
        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        ImportDeduplicationService deduplicationService = new ImportDeduplicationService(
                importedMessageRepository,
                new MimeHashService());

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
    void imapFolderAwareSourceIdentityDoesNotTreatDifferentFolderMessagesWithSameUidAsDuplicates() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "inbox-alpha", "Inbox message", "<imap-folder-a@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026", "project-beta", "Project message", "<imap-folder-b@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        InMemoryReadyCheckpointSourcePollingStateService sourcePollingStateService = new InMemoryReadyCheckpointSourcePollingStateService();

        PollRunResult firstRun = pollService(
                List.of(runtimeAccount("INBOX", DESTINATION_FOLDER)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-folder-identity:first");

        assertEquals(1, firstRun.getFetched());
        assertEquals(1, firstRun.getImported());
        assertEquals(0, firstRun.getDuplicates());

        PollRunResult secondRun = pollService(
                List.of(runtimeAccount("Projects/2026", DESTINATION_FOLDER)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-folder-identity:second");

        assertEquals(1, secondRun.getFetched());
        assertEquals(1, secondRun.getImported());
        assertEquals(0, secondRun.getDuplicates());
        assertEquals(List.of("inbox-alpha", "project-beta"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
    }

    @Test
    void multiFolderImapPollingTracksIndependentPerFolderCheckpointsAndDedupeScopes() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "inbox-alpha", "Inbox message", "<imap-multi-a@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026", "project-beta", "Project message", "<imap-multi-b@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        InMemoryReadyCheckpointSourcePollingStateService sourcePollingStateService = new InMemoryReadyCheckpointSourcePollingStateService();
        RuntimeEmailAccount multiFolderAccount = runtimeAccount("INBOX, Projects/2026", DESTINATION_FOLDER);

        PollRunResult firstRun = pollService(
                List.of(multiFolderAccount),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-multi-folder:first");

        assertEquals(2, firstRun.getFetched());
        assertEquals(2, firstRun.getImported());
        assertEquals(0, firstRun.getDuplicates());
        assertTrue(sourcePollingStateService.imapCheckpoint(
                "greenmail-source",
                DestinationIdentityKeys.forTarget(multiFolderAccount.destination()),
                "INBOX").isPresent());
        assertTrue(sourcePollingStateService.imapCheckpoint(
                "greenmail-source",
                DestinationIdentityKeys.forTarget(multiFolderAccount.destination()),
                "Projects/2026").isPresent());

        PollRunResult secondRun = pollService(
                List.of(multiFolderAccount),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-multi-folder:second");

        assertEquals(2, secondRun.getFetched());
        assertEquals(0, secondRun.getImported());
        assertEquals(2, secondRun.getDuplicates());

        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026", "project-gamma", "Second project message", "<imap-multi-c@example.com>");

        PollRunResult thirdRun = pollService(
                List.of(multiFolderAccount),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-multi-folder:third");

        assertEquals(2, thirdRun.getFetched());
        assertEquals(1, thirdRun.getImported());
        assertEquals(1, thirdRun.getDuplicates());
        assertEquals(
                List.of("inbox-alpha", "project-beta", "project-gamma"),
                listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
    }

    @Test
    void imapMessageIdFallbackStillSuppressesReimportAcrossFolderSwitches() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "inbox-alpha", "Inbox variant", "<imap-stable-message@example.com>");
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026", "project-alpha", "Project variant", "<imap-stable-message@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        InMemoryReadyCheckpointSourcePollingStateService sourcePollingStateService = new InMemoryReadyCheckpointSourcePollingStateService();

        PollRunResult firstRun = pollService(
                List.of(runtimeAccount("INBOX", DESTINATION_FOLDER)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-folder-message-id:first");

        assertEquals(1, firstRun.getFetched());
        assertEquals(1, firstRun.getImported());
        assertEquals(0, firstRun.getDuplicates());

        PollRunResult secondRun = pollService(
                List.of(runtimeAccount("Projects/2026", DESTINATION_FOLDER)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-folder-message-id:second");

        assertEquals(1, secondRun.getFetched());
        assertEquals(0, secondRun.getImported());
        assertEquals(1, secondRun.getDuplicates());
        assertEquals(List.of("inbox-alpha"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
    }

    @Test
    void imapMovedMessageIsRecognizedAsAlreadyImportedAfterFolderSwitch() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "moved-alpha", "Same moved message", "<imap-moved-message@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        InMemoryReadyCheckpointSourcePollingStateService sourcePollingStateService = new InMemoryReadyCheckpointSourcePollingStateService();

        PollRunResult firstRun = pollService(
                List.of(runtimeAccount("INBOX", DESTINATION_FOLDER)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-moved-message:first");

        assertEquals(1, firstRun.getFetched());
        assertEquals(1, firstRun.getImported());
        assertEquals(0, firstRun.getDuplicates());
        assertEquals(List.of("moved-alpha"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));

        moveMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "Projects/2026", "moved-alpha");

        PollRunResult secondRun = pollService(
                List.of(runtimeAccount("Projects/2026", DESTINATION_FOLDER)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPoll("greenmail-integration:imap-moved-message:second");

        assertEquals(1, secondRun.getFetched());
        assertEquals(0, secondRun.getImported());
        assertEquals(1, secondRun.getDuplicates());
        assertEquals(List.of(), listSubjectsIfFolderExists(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX"));
        assertEquals(List.of("moved-alpha"), listSubjects(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026"));
        assertEquals(List.of("moved-alpha"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
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
        MailSourceClient mailSourceClient = standaloneMailSourceClient();
        ImapAppendMailDestinationService destinationService = new ImapAppendMailDestinationService();
        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        ImportDeduplicationService deduplicationService = new ImportDeduplicationService(
                importedMessageRepository,
                new MimeHashService());

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
    void pop3SourcesNeverMixMessagesAcrossDifferentUserDestinations() throws Exception {
        String aliceSource = "alice-pop@example.com";
        String bobSource = "bob-pop@example.com";
        String sharedSourcePassword = "Source#Pop";
        String aliceDestination = "alice-pop-destination@example.com";
        String bobDestination = "bob-pop-destination@example.com";
        String sharedDestinationPassword = "Destination#Pop";

        sourceMail.setUser(aliceSource, sharedSourcePassword);
        sourceMail.setUser(bobSource, sharedSourcePassword);
        destinationMail.setUser(aliceDestination, sharedDestinationPassword);
        destinationMail.setUser(bobDestination, sharedDestinationPassword);

        appendMessage(sourceMail, aliceSource, sharedSourcePassword, "INBOX", "alice-pop-1", "Alice POP message", "<alice-pop-1@example.com>");
        appendMessage(sourceMail, bobSource, sharedSourcePassword, "INBOX", "bob-pop-1", "Bob POP message", "<bob-pop-1@example.com>");

        RecordingImportedMessageRepository importedMessageRepository = new RecordingImportedMessageRepository();
        InMemoryReadyCheckpointSourcePollingStateService sourcePollingStateService = new InMemoryReadyCheckpointSourcePollingStateService();

        PollRunResult result = pollService(
                List.of(
                        runtimePop3Account("alice-pop-source", ALICE_USER_ID, "alice", aliceSource, sharedSourcePassword, "user-destination:7:pop", aliceDestination, sharedDestinationPassword),
                        runtimePop3Account("bob-pop-source", BOB_USER_ID, "bob", bobSource, sharedSourcePassword, "user-destination:8:pop", bobDestination, sharedDestinationPassword)),
                importedMessageRepository,
                sourcePollingStateService)
                .runPollForAllUsers(adminActor(1L), "greenmail-integration:pop3-all-users");

        assertEquals(2, result.getFetched());
        assertEquals(2, result.getImported());
        assertEquals(0, result.getDuplicates());
        assertTrue(result.getErrorDetails().isEmpty());
        assertEquals(List.of("alice-pop-1"), listSubjects(destinationMail, aliceDestination, sharedDestinationPassword, DESTINATION_FOLDER));
        assertEquals(List.of("bob-pop-1"), listSubjects(destinationMail, bobDestination, sharedDestinationPassword, DESTINATION_FOLDER));
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
    void runPollCanApplyPostPollMoveFromSecondaryConfiguredFolder() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026", "delta", "Imported from a secondary folder", "<delta@example.com>");
        ensureFolderExists(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Processed");

        PollRunResult result = pollService(runtimeAccountWithPostPollSettings(
                "INBOX, Projects/2026",
                new SourcePostPollSettings(
                        true,
                        SourcePostPollAction.MOVE,
                        Optional.of("Processed"))))
                .runPoll("greenmail-integration:move-and-read-secondary-folder");

        assertEquals(1, result.getImported());
        assertTrue(result.getErrorDetails().isEmpty());
        assertEquals(List.of("delta"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
        assertEquals(List.of(), listSubjectsIfFolderExists(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Projects/2026"));
        assertEquals(List.of("delta"), listSubjects(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "Processed"));
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

    @Test
    void runPollCanMarkSourceMessagesForwardedAfterImport() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "gamma", "Mark forwarded after import", "<gamma@example.com>");

        PollRunResult result = pollService(runtimeAccountWithPostPollSettings(new SourcePostPollSettings(
                false,
                SourcePostPollAction.FORWARDED,
                Optional.empty())))
                .runPoll("greenmail-integration:forwarded");

        assertEquals(1, result.getImported());
        assertTrue(result.getErrorDetails().isEmpty());
        assertEquals(List.of("gamma"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
        assertTrue(messageHasUserFlag(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "gamma", "$Forwarded"));
    }

    @Test
    void schedulerSkipsIdleSourcesButIdleTriggeredRunsStillImportViaImapAppend() throws Exception {
        appendMessage(sourceMail, SOURCE_USERNAME, SOURCE_PASSWORD, "INBOX", "idle-alpha", "Imported by idle trigger", "<idle-alpha@example.com>");

        RuntimeEmailAccount idleAccount = runtimeAccountWithFetchMode(SourceFetchMode.IDLE);
        PollingService service = pollService(idleAccount);

        PollRunResult schedulerRun = service.runPoll("scheduler");

        assertEquals(0, schedulerRun.getFetched());
        assertEquals(0, schedulerRun.getImported());
        assertTrue(schedulerRun.getErrorDetails().isEmpty());
        assertEquals(List.of(), listSubjectsIfFolderExists(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));

        PollRunResult idleRun = service.runIdleTriggeredPollForSource(idleAccount);

        assertEquals(1, idleRun.getFetched());
        assertEquals(1, idleRun.getImported());
        assertTrue(idleRun.getErrorDetails().isEmpty());
        assertEquals(List.of("idle-alpha"), listSubjects(destinationMail, DESTINATION_USERNAME, DESTINATION_PASSWORD, DESTINATION_FOLDER));
    }

    private PollingService pollService(RuntimeEmailAccount runtimeAccount) {
        return pollService(List.of(runtimeAccount), new RecordingImportedMessageRepository());
    }

    private PollingService pollService(List<RuntimeEmailAccount> runtimeAccounts, RecordingImportedMessageRepository importedMessageRepository) {
        return pollService(runtimeAccounts, importedMessageRepository, new ReadySourcePollingStateService());
    }

    private PollingService pollService(
            List<RuntimeEmailAccount> runtimeAccounts,
            RecordingImportedMessageRepository importedMessageRepository,
            SourcePollingStateService sourcePollingStateService) {
        PollingService service = new PollingService();
        MailSourceClient mailSourceClient = standaloneMailSourceClient(sourcePollingStateService);
        ImapAppendMailDestinationService destinationService = new ImapAppendMailDestinationService();
        ImportDeduplicationService deduplicationService = new ImportDeduplicationService(
                importedMessageRepository,
                new MimeHashService());

        service.mailSourceClient = mailSourceClient;
        service.importDeduplicationService = deduplicationService;
        service.mailDestinationServices = new SingleMailDestinationServices(destinationService);
        service.sourcePollEventService = new NoopSourcePollEventService();
        service.runtimeEmailAccountService = new FixedRuntimeEmailAccountService(runtimeAccounts);
        service.pollingSettingsService = new FixedPollingSettingsService();
        service.userPollingSettingsService = new FixedUserPollingSettingsService();
        service.sourcePollingSettingsService = new FixedSourcePollingSettingsService();
        service.sourcePollingStateService = sourcePollingStateService;
        service.manualPollRateLimitService = new ManualPollRateLimitService();
        return service;
    }

    private RuntimeEmailAccount runtimeAccount() {
        return runtimeAccount("INBOX", DESTINATION_FOLDER);
    }

    private RuntimeEmailAccount runtimeAccount(String sourceFolder, String destinationFolder) {
        return runtimeAccount(sourceFolder, destinationFolder, SourceFetchMode.POLLING);
    }

    private RuntimeEmailAccount runtimeAccountWithFetchMode(SourceFetchMode fetchMode) {
        return runtimeAccount("INBOX", DESTINATION_FOLDER, fetchMode);
    }

    private RuntimeEmailAccount runtimePop3Account() {
        return runtimePop3Account("user-destination:7", DESTINATION_USERNAME, DESTINATION_FOLDER);
    }

    private RuntimeEmailAccount runtimePop3Account(String destinationKey, String destinationUsername, String destinationFolder) {
        return runtimePop3Account(
                "greenmail-source-pop",
                7L,
                "alice",
                SOURCE_USERNAME,
                SOURCE_PASSWORD,
                destinationKey,
                destinationUsername,
                DESTINATION_PASSWORD,
                destinationFolder);
    }

    private RuntimeEmailAccount runtimePop3Account(
            String sourceId,
            Long userId,
            String ownerUsername,
            String sourceUsername,
            String sourcePassword,
            String destinationKey,
            String destinationUsername,
            String destinationPassword) {
        return runtimePop3Account(
                sourceId,
                userId,
                ownerUsername,
                sourceUsername,
                sourcePassword,
                destinationKey,
                destinationUsername,
                destinationPassword,
                DESTINATION_FOLDER);
    }

    private RuntimeEmailAccount runtimePop3Account(
            String sourceId,
            Long userId,
            String ownerUsername,
            String sourceUsername,
            String sourcePassword,
            String destinationKey,
            String destinationUsername,
            String destinationPassword,
            String destinationFolder) {
        return new RuntimeEmailAccount(
                sourceId,
                "USER",
                userId,
                ownerUsername,
                true,
                InboxBridgeConfig.Protocol.POP3,
                "127.0.0.1",
                sourceMail.getPop3().getPort(),
                false,
                InboxBridgeConfig.AuthMethod.PASSWORD,
                InboxBridgeConfig.OAuthProvider.NONE,
                sourceUsername,
                sourcePassword,
                "",
                Optional.empty(),
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
                        destinationFolder));
    }

    private RuntimeEmailAccount runtimeAccount(String sourceFolder, String destinationFolder, SourceFetchMode fetchMode) {
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
                fetchMode,
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
        return runtimeAccountWithPostPollSettings("INBOX", postPollSettings);
    }

    private RuntimeEmailAccount runtimeAccountWithPostPollSettings(String sourceFolders, SourcePostPollSettings postPollSettings) {
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
                Optional.of(sourceFolders),
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
            GreenMailExtension greenMail,
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
            message.setSentDate(java.util.Date.from(Instant.now()));
            message.saveChanges();
            message.setHeader("Message-ID", messageId);
            folder.appendMessages(new Message[] { message });
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private static void moveMessage(
            GreenMailExtension greenMail,
            String username,
            String password,
            String fromFolderName,
            String toFolderName,
            String subject) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        Folder sourceFolder = null;
        Folder targetFolder = null;
        try {
            store.connect("127.0.0.1", greenMail.getImap().getPort(), username, password);
            sourceFolder = store.getFolder(fromFolderName);
            targetFolder = store.getFolder(toFolderName);
            if (!targetFolder.exists()) {
                targetFolder.create(Folder.HOLDS_MESSAGES);
            }
            sourceFolder.open(Folder.READ_WRITE);
            Message targetMessage = null;
            for (Message message : sourceFolder.getMessages()) {
                if (subject.equals(message.getSubject())) {
                    targetMessage = message;
                    break;
                }
            }
            assertTrue(targetMessage != null, "Expected source message to exist before move");
            sourceFolder.copyMessages(new Message[] { targetMessage }, targetFolder);
            targetMessage.setFlag(jakarta.mail.Flags.Flag.DELETED, true);
            sourceFolder.close(true);
            sourceFolder = null;
        } finally {
            closeQuietly(sourceFolder);
            closeQuietly(targetFolder);
            closeQuietly(store);
        }
    }

    private static List<String> listSubjects(GreenMailExtension greenMail, String username, String password, String folderName) throws Exception {
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

    private static void ensureFolderExists(GreenMailExtension greenMail, String username, String password, String folderName) throws Exception {
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

    private static List<String> listSubjectsIfFolderExists(GreenMailExtension greenMail, String username, String password, String folderName) throws Exception {
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

    private static List<String> listUnreadSubjects(GreenMailExtension greenMail, String username, String password, String folderName) throws Exception {
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

    private static List<String> listPopUidls(GreenMailExtension greenMail, String username, String password) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "pop3");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("pop3");
        POP3Folder folder = null;
        try {
            store.connect("127.0.0.1", greenMail.getPop3().getPort(), username, password);
            folder = (POP3Folder) store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            List<String> uidls = new ArrayList<>();
            for (Message message : folder.getMessages()) {
                uidls.add(folder.getUID(message));
            }
            return uidls;
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private static boolean messageHasUserFlag(
            GreenMailExtension greenMail,
            String username,
            String password,
            String folderName,
            String subject,
            String flag) throws Exception {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imap");
        Session session = Session.getInstance(properties);
        Store store = session.getStore("imap");
        Folder folder = null;
        try {
            store.connect("127.0.0.1", greenMail.getImap().getPort(), username, password);
            folder = store.getFolder(folderName);
            if (!folder.exists()) {
                return false;
            }
            folder.open(Folder.READ_ONLY);
            for (Message message : folder.getMessages()) {
                if (subject.equals(message.getSubject())) {
                    return java.util.Arrays.asList(message.getFlags().getUserFlags()).contains(flag);
                }
            }
            return false;
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }
    }

    private static MailSourceClient standaloneMailSourceClient() {
        return standaloneMailSourceClient(new ReadySourcePollingStateService());
    }

    private static MailSourceClient standaloneMailSourceClient(SourcePollingStateService sourcePollingStateService) {
        return MailSourceStandaloneFactory.client(
                new FixedPollingSettingsService(),
                sourcePollingStateService,
                new MimeHashService(),
                null,
                null,
                null);
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

    private static final class RecordingImportedMessageRepository extends ImportedMessageRepository {
        private final List<ImportedMessage> importedMessages = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public boolean existsBySourceMessageKey(String destinationIdentityKey, String sourceAccountId, String sourceMessageKey) {
            return importedMessages.stream().anyMatch((message) ->
                    destinationIdentityKey.equals(message.destinationIdentityKey)
                            && sourceAccountId.equals(message.sourceAccountId)
                            && sourceMessageKey.equals(message.sourceMessageKey));
        }

        @Override
        public boolean existsByRawSha256(String destinationIdentityKey, String rawSha256) {
            return importedMessages.stream().anyMatch((message) ->
                    destinationIdentityKey.equals(message.destinationIdentityKey)
                            && rawSha256.equals(message.rawSha256));
        }

        @Override
        public boolean existsByMessageIdHeader(String destinationIdentityKey, String sourceAccountId, String messageIdHeader) {
            return importedMessages.stream().anyMatch((message) ->
                    destinationIdentityKey.equals(message.destinationIdentityKey)
                            && sourceAccountId.equals(message.sourceAccountId)
                            && messageIdHeader.equals(message.messageIdHeader));
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
        public CooldownDecision recordFailure(String sourceId, Instant finishedAt, String failureReason) {
            return null;
        }
    }

    private static final class InMemoryReadyCheckpointSourcePollingStateService extends SourcePollingStateService {
        private InMemoryReadyCheckpointSourcePollingStateService() {
            super(
                    new InMemorySourcePollingStateRepository(),
                    new InMemorySourceImapCheckpointRepository(),
                    new FixedPollingSettingsService());
        }

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
        public CooldownDecision recordFailure(String sourceId, Instant finishedAt, String failureReason) {
            return null;
        }
    }

    private static final class InMemorySourcePollingStateRepository extends SourcePollingStateRepository {
        private final List<SourcePollingState> states = new java.util.concurrent.CopyOnWriteArrayList<>();
        private long sequence = 1L;

        @Override
        public Optional<SourcePollingState> findBySourceId(String sourceId) {
            return states.stream().filter((state) -> sourceId.equals(state.sourceId)).findFirst();
        }

        @Override
        public java.util.Map<String, SourcePollingState> findBySourceIds(List<String> sourceIds) {
            return states.stream()
                    .filter((state) -> sourceIds.contains(state.sourceId))
                    .collect(java.util.stream.Collectors.toMap((state) -> state.sourceId, (state) -> state));
        }

        @Override
        public void persist(SourcePollingState entity) {
            if (entity.id == null) {
                entity.id = sequence++;
                states.add(entity);
            }
        }
    }

    private static final class InMemorySourceImapCheckpointRepository extends SourceImapCheckpointRepository {
        private final List<SourceImapCheckpoint> checkpoints = new java.util.concurrent.CopyOnWriteArrayList<>();
        private long sequence = 1L;

        @Override
        public Optional<SourceImapCheckpoint> findByScope(String sourceId, String destinationKey, String folderName) {
            return checkpoints.stream()
                    .filter((checkpoint) -> sourceId.equals(checkpoint.sourceId))
                    .filter((checkpoint) -> destinationKey.equals(checkpoint.destinationKey))
                    .filter((checkpoint) -> checkpoint.folderName != null && checkpoint.folderName.equalsIgnoreCase(folderName))
                    .findFirst();
        }

        @Override
        public void persist(SourceImapCheckpoint entity) {
            if (entity.id == null) {
                entity.id = sequence++;
                checkpoints.add(entity);
            }
        }
    }

    private static final class NoopSourcePollEventService extends SourcePollEventService {
        @Override
        public void record(String sourceId, String trigger, Instant startedAt, Instant finishedAt, int fetched, int imported, long importedBytes, int duplicates, int spamJunkMessageCount, String actorUsername, String executionSurface, String error, PollDecisionSnapshot decisionSnapshot) {
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
