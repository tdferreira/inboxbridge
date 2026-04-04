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

/**
 * Durable event log for each source poll attempt.
 */
@Entity
@Table(name = "source_poll_event",
        indexes = {
                @Index(name = "idx_source_poll_event_source", columnList = "source_id"),
                @Index(name = "idx_source_poll_event_finished", columnList = "finished_at")
        })
public class SourcePollEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "source_id", nullable = false, length = 100)
    public String sourceId;

    @Column(name = "trigger_name", nullable = false, length = 50)
    public String triggerName;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "started_at", nullable = false)
    public Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    public Instant finishedAt;

    @Column(name = "fetched_count", nullable = false)
    public int fetchedCount;

    @Column(name = "imported_count", nullable = false)
    public int importedCount;

    @Column(name = "imported_bytes", nullable = false)
    public long importedBytes;

    @Column(name = "duplicate_count", nullable = false)
    public int duplicateCount;

    @Column(name = "spam_junk_message_count", nullable = false)
    public int spamJunkMessageCount;

    @Column(name = "actor_username", length = 120)
    public String actorUsername;

    @Column(name = "execution_surface", length = 40)
    public String executionSurface;

    @Column(name = "error_message", length = 4000)
    public String errorMessage;
}
