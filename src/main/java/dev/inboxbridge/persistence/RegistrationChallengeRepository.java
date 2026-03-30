package dev.inboxbridge.persistence;

import java.time.Instant;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class RegistrationChallengeRepository implements PanacheRepository<RegistrationChallenge> {

    public Optional<RegistrationChallenge> findByToken(String challengeToken) {
        return find("challengeToken", challengeToken).firstResultOptional();
    }

    @Transactional
    public long deleteExpired(Instant now) {
        return delete("expiresAt < ?1", now);
    }
}
