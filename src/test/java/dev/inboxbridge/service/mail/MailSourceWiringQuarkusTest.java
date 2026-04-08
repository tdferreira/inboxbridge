package dev.inboxbridge.service.mail;


import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;

@QuarkusComponentTest
@TestConfigProperty(key = "inboxbridge.mail.connection-timeout", value = "PT5S")
@TestConfigProperty(key = "inboxbridge.mail.operation-timeout", value = "PT11S")
@TestConfigProperty(key = "inboxbridge.mail.idle-operation-timeout", value = "PT0S")
class MailSourceWiringQuarkusTest {

    @Inject
    MailSourceClient mailSourceClient;

    @Inject
    MailSourceFetchService mailSourceFetchService;

    @Inject
    MailSourceConnectionProbeService mailSourceConnectionProbeService;

    @Inject
    MailSourcePostPollActionService mailSourcePostPollActionService;

    @Inject
    MailSourceConnectionService mailSourceConnectionService;

    @Test
    void quarkusCanConstructMailSourceSliceWithConstructorInjectedHelpers() {
        assertNotNull(mailSourceClient);
        assertNotNull(mailSourceFetchService);
        assertNotNull(mailSourceConnectionProbeService);
        assertNotNull(mailSourcePostPollActionService);
        assertNotNull(mailSourceConnectionService);
    }
}
