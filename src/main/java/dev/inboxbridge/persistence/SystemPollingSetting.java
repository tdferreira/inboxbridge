package dev.inboxbridge.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Stores optional system-wide polling overrides managed from the admin UI.
 *
 * <p>The environment remains the source of default values. This table only
 * stores admin-selected overrides for the poll enabled flag, scheduler
 * interval, and mailbox fetch window.</p>
 */
@Entity
@Table(name = "system_polling_setting")
public class SystemPollingSetting extends PanacheEntityBase {

    public static final long SINGLETON_ID = 1L;

    @Id
    public Long id;

    @Column(name = "poll_enabled_override")
    public Boolean pollEnabledOverride;

    @Column(name = "poll_interval_override", length = 32)
    public String pollIntervalOverride;

    @Column(name = "fetch_window_override")
    public Integer fetchWindowOverride;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
