package dev.inboxbridge.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

import dev.inboxbridge.dto.RegistrationChallengeResponse;
import dev.inboxbridge.persistence.RegistrationChallenge;
import dev.inboxbridge.persistence.RegistrationChallengeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RegistrationChallengeService {

    @Inject
    RegistrationChallengeRepository repository;

    @Inject
    AuthSecuritySettingsService authSecuritySettingsService;

    public boolean enabled() {
        return authSecuritySettingsService.effectiveSettings().registrationChallengeEnabled();
    }

    public RegistrationChallengeResponse currentChallenge() {
        if (!enabled()) {
            return RegistrationChallengeResponse.disabled();
        }
        return issueChallenge();
    }

    @Transactional
    public RegistrationChallengeResponse issueChallenge() {
        repository.deleteExpired(Instant.now());
        int left = ThreadLocalRandom.current().nextInt(2, 10);
        int right = ThreadLocalRandom.current().nextInt(2, 10);
        RegistrationChallenge challenge = new RegistrationChallenge();
        challenge.challengeToken = java.util.UUID.randomUUID().toString();
        challenge.prompt = left + " + " + right + " = ?";
        challenge.answerHash = hashAnswer(Integer.toString(left + right));
        challenge.createdAt = Instant.now();
        challenge.expiresAt = challenge.createdAt.plus(authSecuritySettingsService.effectiveSettings().registrationChallengeTtl());
        repository.persist(challenge);
        return new RegistrationChallengeResponse(true, challenge.challengeToken, challenge.prompt);
    }

    @Transactional
    public void validateAndConsume(String challengeId, String challengeAnswer) {
        if (!enabled()) {
            return;
        }
        repository.deleteExpired(Instant.now());
        if (challengeId == null || challengeId.isBlank() || challengeAnswer == null || challengeAnswer.isBlank()) {
            throw new IllegalArgumentException("Registration challenge answer is required");
        }
        RegistrationChallenge challenge = repository.findByToken(challengeId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Registration challenge is invalid or expired"));
        repository.delete(challenge);
        if (challenge.expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Registration challenge is invalid or expired");
        }
        if (!challenge.answerHash.equals(hashAnswer(challengeAnswer.trim()))) {
            throw new IllegalArgumentException("Registration challenge answer is incorrect");
        }
    }

    private String hashAnswer(String answer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(answer.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
