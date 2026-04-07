package dev.inboxbridge.web.polling;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.smallrye.common.annotation.Blocking;

class PollingResourceTest {

    @Test
    void eventsIsBlocking() throws NoSuchMethodException {
        assertTrue(PollingResource.class.getMethod("events").isAnnotationPresent(Blocking.class));
    }
}
