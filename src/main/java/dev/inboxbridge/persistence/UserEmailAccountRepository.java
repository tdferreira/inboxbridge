package dev.inboxbridge.persistence;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserEmailAccountRepository implements PanacheRepository<UserEmailAccount> {

    public List<UserEmailAccount> listByUserId(Long userId) {
        return find("userId = ?1 order by bridgeId", userId).list();
    }

    public Optional<UserEmailAccount> findByBridgeId(String bridgeId) {
        return find("bridgeId", bridgeId).firstResultOptional();
    }

    @Transactional
    public long deleteByUserId(Long userId) {
        return delete("userId", userId);
    }
}
