package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserPollingSettingRepository implements PanacheRepository<UserPollingSetting> {

    public Optional<UserPollingSetting> findByUserId(Long userId) {
        return find("userId", userId).firstResultOptional();
    }

    @Transactional
    public long deleteByUserId(Long userId) {
        return delete("userId", userId);
    }
}
