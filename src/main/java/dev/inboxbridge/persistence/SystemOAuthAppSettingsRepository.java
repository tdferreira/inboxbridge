package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SystemOAuthAppSettingsRepository implements PanacheRepository<SystemOAuthAppSettings> {
    public Optional<SystemOAuthAppSettings> findSingleton() {
        return findByIdOptional(1L);
    }
}
