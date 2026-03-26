package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHashServiceTest {

    @Test
    void hashMatchesOriginalPasswordButNotAnotherPassword() {
        PasswordHashService service = new PasswordHashService();

        String hash = service.hash("nimda");

        assertTrue(service.matches("nimda", hash));
        assertFalse(service.matches("admin", hash));
    }
}
