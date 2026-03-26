package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class PollingServiceTest {

    @Test
    void scheduledPollRunsWhenPollingIsEnabled() {
        RecordingPollingService service = new RecordingPollingService();
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofSeconds(5), "5s", 10);

        service.scheduledPoll();

        assertEquals("scheduler", service.lastTrigger);
    }

    @Test
    void scheduledPollSkipsWhenDisabled() {
        RecordingPollingService service = new RecordingPollingService();
        service.pollingSettingsService = new FakePollingSettingsService(false, Duration.ofSeconds(5), "5s", 10);

        service.scheduledPoll();

        assertEquals(null, service.lastTrigger);
    }

    @Test
    void scheduledPollRespectsEffectiveInterval() {
        RecordingPollingService service = new RecordingPollingService();
        service.pollingSettingsService = new FakePollingSettingsService(true, Duration.ofHours(1), "1h", 10);

        service.scheduledPoll();
        service.scheduledPoll();

        assertEquals(1, service.invocations);
    }

    private static final class RecordingPollingService extends PollingService {
        private String lastTrigger;
        private int invocations;

        @Override
        public dev.inboxbridge.dto.PollRunResult runPoll(String trigger) {
            lastTrigger = trigger;
            invocations++;
            return new dev.inboxbridge.dto.PollRunResult();
        }
    }

    private static final class FakePollingSettingsService extends PollingSettingsService {
        private final EffectivePollingSettings settings;

        private FakePollingSettingsService(boolean enabled, Duration interval, String text, int fetchWindow) {
            this.settings = new EffectivePollingSettings(enabled, text, interval, fetchWindow);
        }

        @Override
        public EffectivePollingSettings effectiveSettings() {
            return settings;
        }
    }
}
