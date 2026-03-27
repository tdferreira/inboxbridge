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
 * Stores optional per-source polling overrides that take precedence over the
 * owning user's polling settings and the global defaults.
 */
@Entity
@Table(name = "source_polling_setting",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_source_polling_setting_source", columnNames = { "source_id" })
        },
        indexes = {
                @Index(name = "idx_source_polling_setting_source", columnList = "source_id"),
                @Index(name = "idx_source_polling_setting_user", columnList = "owner_user_id")
        })
public class SourcePollingSetting extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "source_id", nullable = false, length = 120)
    public String sourceId;

    @Column(name = "owner_user_id")
    public Long ownerUserId;

    @Column(name = "poll_enabled_override")
    public Boolean pollEnabledOverride;

    @Column(name = "poll_interval_override", length = 40)
    public String pollIntervalOverride;

    @Column(name = "fetch_window_override")
    public Integer fetchWindowOverride;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
