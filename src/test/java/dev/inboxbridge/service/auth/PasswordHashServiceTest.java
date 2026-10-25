package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHashServiceTest {

    @Test
    void hashMatchesOriginalPasswordButNotAnotherPassword() {
        PasswordHashService service = new PasswordHashService();

        String hash = service.hash("nimda");

        assertTrue(hash.startsWith("pbkdf2$600000$"));
        assertFalse(service.needsRehash(hash));
        assertTrue(service.matches("nimda", hash));
        assertFalse(service.matches("admin", hash));
    }

    @Test
    void existingHashesRemainVerifiableAndAreMarkedForUpgrade() {
        var service = new PasswordHashService();
        String legacy = "pbkdf2$210000$MDEyMzQ1Njc4OWFiY2RlZg==$5/EWREJlVVJn83iPfVPTZQzCf1TyjiiDFDRMqYxkZpc=";
        assertTrue(service.matches("Legacy#123", legacy));
        assertFalse(service.matches("wrong", legacy));
        assertTrue(service.needsRehash(legacy));
    }
}
