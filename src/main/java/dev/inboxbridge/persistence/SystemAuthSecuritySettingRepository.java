package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SystemAuthSecuritySettingRepository implements PanacheRepository<SystemAuthSecuritySetting> {

    public Optional<SystemAuthSecuritySetting> findSingleton() {
        return findByIdOptional(SystemAuthSecuritySetting.SINGLETON_ID);
    }
}
