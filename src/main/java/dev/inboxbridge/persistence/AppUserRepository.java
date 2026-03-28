package dev.inboxbridge.persistence;

import java.util.List;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AppUserRepository implements PanacheRepository<AppUser> {

    public Optional<AppUser> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }

    public Optional<AppUser> findByUserHandle(String userHandle) {
        return find("userHandle", userHandle).firstResultOptional();
    }

    public long countApprovedAdmins() {
        return count("role = ?1 and active = true and approved = true", AppUser.Role.ADMIN);
    }

    public List<AppUser> listDisabledBySingleUserMode() {
        return list("disabledBySingleUserMode = true order by username");
    }
}
