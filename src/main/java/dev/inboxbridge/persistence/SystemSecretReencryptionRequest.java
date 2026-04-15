package dev.inboxbridge.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_secret_reencryption_request")
public class SystemSecretReencryptionRequest extends PanacheEntityBase {

    public static final long SINGLETON_ID = 1L;

    @Id
    public Long id;

    @Column(name = "status", nullable = false, length = 32)
    public String status;

    @Column(name = "requested_at")
    public Instant requestedAt;

    @Column(name = "requested_by_user_id")
    public Long requestedByUserId;

    @Column(name = "execute_after")
    public Instant executeAfter;

    @Column(name = "immediate_execution_override", nullable = false)
    public boolean immediateExecutionOverride;

    @Column(name = "revoke_browser_extension_sessions", nullable = false)
    public boolean revokeBrowserExtensionSessions;

    @Column(name = "revoke_remote_sessions", nullable = false)
    public boolean revokeRemoteSessions;

    @Column(name = "clear_cached_oauth_access_tokens", nullable = false)
    public boolean clearCachedOAuthAccessTokens;

    @Column(name = "last_started_at")
    public Instant lastStartedAt;

    @Column(name = "last_completed_at")
    public Instant lastCompletedAt;

    @Column(name = "last_failed_at")
    public Instant lastFailedAt;

    @Column(name = "last_error_message", length = 500)
    public String lastErrorMessage;

    @Column(name = "last_result_message", length = 500)
    public String lastResultMessage;

    @Column(name = "last_verification_passed")
    public Boolean lastVerificationPassed;
}
