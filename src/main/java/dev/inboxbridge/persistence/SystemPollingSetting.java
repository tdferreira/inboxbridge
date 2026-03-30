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
 * interval, mailbox fetch window, manual trigger rate limiting, and the
 * provider/host-aware throttling controls.</p>
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

    @Column(name = "manual_trigger_limit_count_override")
    public Integer manualTriggerLimitCountOverride;

    @Column(name = "manual_trigger_limit_window_seconds_override")
    public Integer manualTriggerLimitWindowSecondsOverride;

    @Column(name = "source_host_min_spacing_override")
    public String sourceHostMinSpacingOverride;

    @Column(name = "source_host_max_concurrency_override")
    public Integer sourceHostMaxConcurrencyOverride;

    @Column(name = "destination_provider_min_spacing_override")
    public String destinationProviderMinSpacingOverride;

    @Column(name = "destination_provider_max_concurrency_override")
    public Integer destinationProviderMaxConcurrencyOverride;

    @Column(name = "throttle_lease_ttl_override")
    public String throttleLeaseTtlOverride;

    @Column(name = "adaptive_throttle_max_multiplier_override")
    public Integer adaptiveThrottleMaxMultiplierOverride;

    @Column(name = "success_jitter_ratio_override")
    public Double successJitterRatioOverride;

    @Column(name = "max_success_jitter_override")
    public String maxSuccessJitterOverride;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
