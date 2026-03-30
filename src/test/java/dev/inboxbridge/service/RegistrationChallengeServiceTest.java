package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.dto.RegistrationChallengeResponse;
import dev.inboxbridge.persistence.RegistrationChallenge;
import dev.inboxbridge.persistence.RegistrationChallengeRepository;

class RegistrationChallengeServiceTest {

    @Test
    void issueChallengeCreatesLanguageNeutralPromptAndStoresHash() {
        RegistrationChallengeService service = new RegistrationChallengeService();
        InMemoryRegistrationChallengeRepository repository = new InMemoryRegistrationChallengeRepository();
        service.repository = repository;
        service.authSecuritySettingsService = authSecuritySettingsService(true);

        RegistrationChallengeResponse response = service.issueChallenge();

        assertTrue(response.enabled());
        assertNotNull(response.challengeId());
        assertTrue(response.prompt().matches("\\d+ \\+ \\d+ = \\?"));
        assertEquals(1, repository.size());
    }

    @Test
    void validateAndConsumeRejectsWrongAnswerAndConsumesChallenge() {
        RegistrationChallengeService service = new RegistrationChallengeService();
        InMemoryRegistrationChallengeRepository repository = new InMemoryRegistrationChallengeRepository();
        service.repository = repository;
        service.authSecuritySettingsService = authSecuritySettingsService(true);

        RegistrationChallengeResponse response = service.issueChallenge();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.validateAndConsume(response.challengeId(), "999"));

        assertEquals("Registration challenge answer is incorrect", error.getMessage());
        assertFalse(repository.findByToken(response.challengeId()).isPresent());
    }

    @Test
    void disabledModeSkipsChallengeValidation() {
        RegistrationChallengeService service = new RegistrationChallengeService();
        service.repository = new InMemoryRegistrationChallengeRepository();
        service.authSecuritySettingsService = authSecuritySettingsService(false);

        assertEquals(false, service.currentChallenge().enabled());
        service.validateAndConsume(null, null);
    }

    private AuthSecuritySettingsService authSecuritySettingsService(boolean enabled) {
        return new AuthSecuritySettingsService() {
            @Override
            public EffectiveAuthSecuritySettings effectiveSettings() {
                return new EffectiveAuthSecuritySettings(
                        5,
                        Duration.ofMinutes(5),
                        Duration.ofHours(1),
                        enabled,
                        Duration.ofMinutes(10));
            }
        };
    }

    private static final class InMemoryRegistrationChallengeRepository extends RegistrationChallengeRepository {
        private final Map<String, RegistrationChallenge> challenges = new LinkedHashMap<>();

        @Override
        public Optional<RegistrationChallenge> findByToken(String challengeToken) {
            return Optional.ofNullable(challenges.get(challengeToken));
        }

        @Override
        public void persist(RegistrationChallenge entity) {
            challenges.put(entity.challengeToken, entity);
        }

        @Override
        public long deleteExpired(Instant now) {
            long before = challenges.size();
            challenges.entrySet().removeIf((entry) -> entry.getValue().expiresAt != null && entry.getValue().expiresAt.isBefore(now));
            return before - challenges.size();
        }

        @Override
        public void delete(RegistrationChallenge entity) {
            if (entity != null) {
                challenges.remove(entity.challengeToken);
            }
        }

        private int size() {
            return challenges.size();
        }
    }
}
