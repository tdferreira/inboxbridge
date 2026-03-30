package dev.inboxbridge.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Stores optional system-wide authentication abuse-protection overrides managed
 * from the admin UI.
 */
@Entity
@Table(name = "system_auth_security_setting")
public class SystemAuthSecuritySetting extends PanacheEntityBase {

    public static final long SINGLETON_ID = 1L;

    @Id
    public Long id;

    @Column(name = "login_failure_threshold_override")
    public Integer loginFailureThresholdOverride;

    @Column(name = "login_initial_block_override", length = 40)
    public String loginInitialBlockOverride;

    @Column(name = "login_max_block_override", length = 40)
    public String loginMaxBlockOverride;

    @Column(name = "registration_challenge_enabled_override")
    public Boolean registrationChallengeEnabledOverride;

    @Column(name = "registration_challenge_ttl_override", length = 40)
    public String registrationChallengeTtlOverride;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
