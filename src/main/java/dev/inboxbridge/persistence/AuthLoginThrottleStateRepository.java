package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuthLoginThrottleStateRepository implements PanacheRepository<AuthLoginThrottleState> {

    public Optional<AuthLoginThrottleState> findByClientKey(String clientKey) {
        return find("clientKey", clientKey).firstResultOptional();
    }
}
