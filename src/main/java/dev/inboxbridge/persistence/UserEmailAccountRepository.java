package dev.inboxbridge.persistence;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserEmailAccountRepository implements PanacheRepository<UserEmailAccount> {

    public List<UserEmailAccount> listByUserId(Long userId) {
        return find("userId = ?1 order by emailAccountId", userId).list();
    }

    @Transactional
    public Optional<UserEmailAccount> findByEmailAccountId(String emailAccountId) {
        return find("emailAccountId", emailAccountId).firstResultOptional();
    }

    @Transactional
    public long deleteByUserId(Long userId) {
        return delete("userId", userId);
    }
}
