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
 * Stores optional per-user polling overrides that layer on top of the global
 * poller defaults.
 */
@Entity
@Table(name = "user_polling_setting",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_polling_setting_user", columnNames = { "user_id" })
        },
        indexes = {
                @Index(name = "idx_user_polling_setting_user", columnList = "user_id")
        })
public class UserPollingSetting extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "poll_enabled_override")
    public Boolean pollEnabledOverride;

    @Column(name = "poll_interval_override", length = 40)
    public String pollIntervalOverride;

    @Column(name = "fetch_window_override")
    public Integer fetchWindowOverride;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
