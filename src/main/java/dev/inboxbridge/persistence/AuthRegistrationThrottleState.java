package dev.inboxbridge.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "auth_registration_throttle_state",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_auth_registration_throttle_state_client_key", columnNames = { "client_key" })
        },
        indexes = {
                @Index(name = "idx_auth_registration_throttle_blocked_until", columnList = "blocked_until")
        })
public class AuthRegistrationThrottleState extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "client_key", nullable = false, length = 180)
    public String clientKey;

    @Column(name = "failure_count", nullable = false)
    public int failureCount;

    @Column(name = "lockout_count", nullable = false)
    public int lockoutCount;

    @Column(name = "blocked_until")
    public Instant blockedUntil;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
