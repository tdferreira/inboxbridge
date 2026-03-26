package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OAuthCredentialRepository implements PanacheRepository<OAuthCredential> {

    public Optional<OAuthCredential> findByProviderAndSubject(String provider, String subjectKey) {
        return find("provider = ?1 and subjectKey = ?2", provider, subjectKey).firstResultOptional();
    }
}
