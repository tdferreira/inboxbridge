package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PasskeyServiceTest {

    @Test
    void normalizeAdditionalOriginsKeepsOnlySupportedExtensionOrigins() {
        PasskeyService service = new PasskeyService();

        Set<String> normalized = service.normalizeAdditionalOrigins(List.of(
                " chrome-extension://abc123 ",
                "moz-extension://def456",
                "https://mail.example.com",
                "javascript:alert(1)",
                "safari-web-extension://com.example.inboxbridge",
                ""));

        assertEquals(Set.of(
                "chrome-extension://abc123",
                "moz-extension://def456",
                "safari-web-extension://com.example.inboxbridge"),
                normalized);
    }
}
