package dev.inboxbridge.persistence;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserPasskeyRepository implements PanacheRepository<UserPasskey> {

    public List<UserPasskey> listByUserId(Long userId) {
        return list("userId", userId);
    }

    public Optional<UserPasskey> findByCredentialId(String credentialId) {
        return find("credentialId", credentialId).firstResultOptional();
    }

    public long countByUserId(Long userId) {
        return count("userId", userId);
    }

    @Transactional
    public long deleteByUserId(Long userId) {
        return delete("userId", userId);
    }
}
