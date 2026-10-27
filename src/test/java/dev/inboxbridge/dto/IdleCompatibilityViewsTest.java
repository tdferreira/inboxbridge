package dev.inboxbridge.dto;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdleCompatibilityViewsTest {
    @Test
    void legacyStatisticsDoNotInventRecoveryCounts() {
        var view=new GlobalPollingStatsView(7,2,2,0,0,List.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),null,List.of(),1,2,3,40);
        assertEquals(7,view.totalImportedMessages());
        assertEquals(3,view.idleRuns());
        assertEquals(0,view.catchUpRuns());
    }
    @Test
    void legacySettingsInheritTheSafetyDeadlineAndWatchersDoNotInventAcknowledgements() {
        var settings=new IdleSettingsView(true,null,true,"PT25M",null,"PT25M","PT20S",null,"PT20S");
        assertEquals("PT30M",settings.defaultSafetyPollInterval());
        assertNull(settings.safetyPollIntervalOverride());
        assertEquals("PT30M",settings.effectiveSafetyPollInterval());
        var connected=Instant.parse("2026-10-26T10:00:00Z");
        var watch=new SourceIdleWatchView("INBOX","CONNECTED",connected,null);
        assertEquals(connected,watch.lastConnectedAt());
        assertNull(watch.lastRenewedAt());
    }
}
