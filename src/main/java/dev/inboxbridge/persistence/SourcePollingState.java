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

/**
 * Tracks the current scheduler/backoff state for each source so the poller can
 * honor per-source intervals and temporary cooldowns after provider failures.
 */
@Entity
@Table(name = "source_polling_state",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_source_polling_state_source", columnNames = { "source_id" })
        },
        indexes = {
                @Index(name = "idx_source_polling_state_source", columnList = "source_id"),
                @Index(name = "idx_source_polling_state_next_poll", columnList = "next_poll_at"),
                @Index(name = "idx_source_polling_state_cooldown", columnList = "cooldown_until")
        })
public class SourcePollingState extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "source_id", nullable = false, length = 120)
    public String sourceId;

    @Column(name = "next_poll_at")
    public Instant nextPollAt;

    @Column(name = "cooldown_until")
    public Instant cooldownUntil;

    @Column(name = "consecutive_failures", nullable = false)
    public int consecutiveFailures;

    @Column(name = "last_failure_reason", length = 4000)
    public String lastFailureReason;

    @Column(name = "last_failure_at")
    public Instant lastFailureAt;

    @Column(name = "last_success_at")
    public Instant lastSuccessAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
