package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SessionDeviceLocationFormatterTest {

    @Test
    void returnsNullWhenCoordinatesAreIncomplete() {
        assertNull(SessionDeviceLocationFormatter.format(null, -9.14d, 10d));
        assertNull(SessionDeviceLocationFormatter.format(38.72d, null, 10d));
    }

    @Test
    void formatsCoordinatesWithoutAccuracyWhenAccuracyIsMissingOrNonPositive() {
        assertEquals("38.7223, -9.1393", SessionDeviceLocationFormatter.format(38.72231d, -9.13933d, null));
        assertEquals("38.7223, -9.1393", SessionDeviceLocationFormatter.format(38.72231d, -9.13933d, 0d));
        assertEquals("38.7223, -9.1393", SessionDeviceLocationFormatter.format(38.72231d, -9.13933d, -5d));
    }

    @Test
    void formatsCoordinatesWithRoundedAccuracyWhenAvailable() {
        assertEquals("38.7223, -9.1393 (±13 m)", SessionDeviceLocationFormatter.format(38.72231d, -9.13933d, 12.6d));
    }
}
