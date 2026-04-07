package dev.inboxbridge.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

class WebResourceSupportTest {

    @Test
    void supplierWrapsIllegalArgumentExceptionAsBadRequest() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> WebResourceSupport.badRequest(() -> {
                    throw new IllegalArgumentException("boom");
                }));

        assertEquals("boom", exception.getMessage());
        assertEquals(IllegalArgumentException.class, exception.getCause().getClass());
    }

    @Test
    void runnableWrapsIllegalStateExceptionAsBadRequest() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> WebResourceSupport.badRequest(() -> {
                    throw new IllegalStateException("not-ready");
                }));

        assertEquals("not-ready", exception.getMessage());
        assertEquals(IllegalStateException.class, exception.getCause().getClass());
    }
}
