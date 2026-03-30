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
@Table(name = "poll_throttle_state",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_poll_throttle_state_key", columnNames = { "throttle_key" })
        },
        indexes = {
                @Index(name = "idx_poll_throttle_state_key", columnList = "throttle_key"),
                @Index(name = "idx_poll_throttle_state_next_allowed", columnList = "next_allowed_at")
        })
public class PollThrottleState extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "throttle_key", nullable = false, length = 180)
    public String throttleKey;

    @Column(name = "throttle_kind", nullable = false, length = 40)
    public String throttleKind;

    @Column(name = "next_allowed_at")
    public Instant nextAllowedAt;

    @Column(name = "adaptive_multiplier", nullable = false)
    public int adaptiveMultiplier;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
