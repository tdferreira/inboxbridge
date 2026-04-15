package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SystemSecretReencryptionRequestRepository implements PanacheRepository<SystemSecretReencryptionRequest> {

    public Optional<SystemSecretReencryptionRequest> findSingleton() {
        return findByIdOptional(SystemSecretReencryptionRequest.SINGLETON_ID);
    }
}
