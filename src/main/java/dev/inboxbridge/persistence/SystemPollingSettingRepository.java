package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SystemPollingSettingRepository implements PanacheRepository<SystemPollingSetting> {

    public Optional<SystemPollingSetting> findSingleton() {
        return findByIdOptional(SystemPollingSetting.SINGLETON_ID);
    }
}
