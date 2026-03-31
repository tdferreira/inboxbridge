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

    @Column(name = "registration_challenge_provider_override", length = 40)
    public String registrationChallengeProviderOverride;

    @Column(name = "registration_turnstile_site_key_override", length = 255)
    public String registrationTurnstileSiteKeyOverride;

    @Column(name = "registration_turnstile_secret_ciphertext")
    public String registrationTurnstileSecretCiphertext;

    @Column(name = "registration_turnstile_secret_nonce")
    public String registrationTurnstileSecretNonce;

    @Column(name = "registration_hcaptcha_site_key_override", length = 255)
    public String registrationHcaptchaSiteKeyOverride;

    @Column(name = "registration_hcaptcha_secret_ciphertext")
    public String registrationHcaptchaSecretCiphertext;

    @Column(name = "registration_hcaptcha_secret_nonce")
    public String registrationHcaptchaSecretNonce;

    @Column(name = "geo_ip_enabled_override")
    public Boolean geoIpEnabledOverride;

    @Column(name = "geo_ip_primary_provider_override", length = 40)
    public String geoIpPrimaryProviderOverride;

    @Column(name = "geo_ip_fallback_providers_override", length = 200)
    public String geoIpFallbackProvidersOverride;

    @Column(name = "geo_ip_cache_ttl_override", length = 40)
    public String geoIpCacheTtlOverride;

    @Column(name = "geo_ip_provider_cooldown_override", length = 40)
    public String geoIpProviderCooldownOverride;

    @Column(name = "geo_ip_request_timeout_override", length = 40)
    public String geoIpRequestTimeoutOverride;

    @Column(name = "geo_ip_ipinfo_token_ciphertext")
    public String geoIpIpinfoTokenCiphertext;

    @Column(name = "geo_ip_ipinfo_token_nonce")
    public String geoIpIpinfoTokenNonce;

    @Column(name = "key_version", length = 32)
    public String keyVersion;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
