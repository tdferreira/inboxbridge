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

@Entity
@Table(name = "system_secret_recovery_review", indexes = {
        @Index(name = "idx_system_secret_recovery_review_reviewed_at", columnList = "reviewed_at")
})
public class SystemSecretRecoveryReview extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "reviewed_at", nullable = false)
    public Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    public Long reviewedByUserId;

    @Column(name = "reviewed_by_username", length = 120)
    public String reviewedByUsername;

    @Column(name = "request_fingerprint", nullable = false, length = 255)
    public String requestFingerprint;

    @Column(name = "latest_request_status", length = 32)
    public String latestRequestStatus;

    @Column(name = "latest_request_message", length = 500)
    public String latestRequestMessage;

    @Column(name = "verification_passed", nullable = false)
    public boolean verificationPassed;

    @Column(name = "rollback_recommended", nullable = false)
    public boolean rollbackRecommended;

    @Column(name = "mode", length = 64)
    public String mode;

    @Column(name = "provider_id", length = 64)
    public String providerId;

    @Column(name = "active_key_version", length = 160)
    public String activeKeyVersion;

    @Column(name = "provider_writable", nullable = false)
    public boolean providerWritable;

    @Column(name = "unavailable_key_record_count", nullable = false)
    public long unavailableKeyRecordCount;

    @Column(name = "status_snapshot_json", nullable = false, columnDefinition = "text")
    public String statusSnapshotJson;
}
