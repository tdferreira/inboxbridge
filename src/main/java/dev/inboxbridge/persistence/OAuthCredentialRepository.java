package dev.inboxbridge.persistence;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OAuthCredentialRepository implements PanacheRepository<OAuthCredential> {

    public Optional<OAuthCredential> findByProviderAndSubject(String provider, String subjectKey) {
        return find("provider = ?1 and subjectKey = ?2", provider, subjectKey).firstResultOptional();
    }

    @Transactional
    public long deleteByProviderAndSubject(String provider, String subjectKey) {
        return delete("provider = ?1 and subjectKey = ?2", provider, subjectKey);
    }

    @Transactional
    public long deleteByProviderAndSubjects(String provider, List<String> subjectKeys) {
        if (subjectKeys == null || subjectKeys.isEmpty()) {
            return 0;
        }
        return delete("provider = ?1 and subjectKey in ?2", provider, subjectKeys);
    }
}
