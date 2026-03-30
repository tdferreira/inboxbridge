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
@Table(name = "poll_throttle_lease",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_poll_throttle_lease_token", columnNames = { "lease_token" })
        },
        indexes = {
                @Index(name = "idx_poll_throttle_lease_key", columnList = "throttle_key"),
                @Index(name = "idx_poll_throttle_lease_expiry", columnList = "expires_at")
        })
public class PollThrottleLease extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "throttle_key", nullable = false, length = 180)
    public String throttleKey;

    @Column(name = "lease_token", nullable = false, length = 80)
    public String leaseToken;

    @Column(name = "acquired_at", nullable = false)
    public Instant acquiredAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;
}
