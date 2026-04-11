package dev.inboxbridge.persistence;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AuthRegistrationThrottleStateRepository implements PanacheRepository<AuthRegistrationThrottleState> {

    public Optional<AuthRegistrationThrottleState> findByClientKey(String clientKey) {
        return find("clientKey", clientKey).firstResultOptional();
    }
}
