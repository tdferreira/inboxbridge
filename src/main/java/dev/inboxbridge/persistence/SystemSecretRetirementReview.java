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
@Table(name = "system_secret_retirement_review", indexes = {
        @Index(name = "idx_system_secret_retirement_review_reviewed_at", columnList = "reviewed_at")
})
public class SystemSecretRetirementReview extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "reviewed_at", nullable = false)
    public Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    public Long reviewedByUserId;

    @Column(name = "reviewed_by_username", length = 120)
    public String reviewedByUsername;

    @Column(name = "provider_id", length = 64)
    public String providerId;

    @Column(name = "active_key_version", length = 160)
    public String activeKeyVersion;

    @Column(name = "active_key_id", length = 160)
    public String activeKeyId;

    @Column(name = "legacy_key_ids_json", columnDefinition = "text")
    public String legacyKeyIdsJson;

    @Column(name = "safe_to_retire_legacy_keys", nullable = false)
    public boolean safeToRetireLegacyKeys;

    @Column(name = "legacy_key_retirement_ready", nullable = false)
    public boolean legacyKeyRetirementReady;

    @Column(name = "non_active_key_record_count", nullable = false)
    public long nonActiveKeyRecordCount;

    @Column(name = "unavailable_key_record_count", nullable = false)
    public long unavailableKeyRecordCount;

    @Column(name = "latest_request_status", length = 32)
    public String latestRequestStatus;

    @Column(name = "blocking_requirements_remaining", nullable = false)
    public int blockingRequirementsRemaining;

    @Column(name = "unsatisfied_requirement_ids_json", columnDefinition = "text")
    public String unsatisfiedRequirementIdsJson;

    @Column(name = "status_snapshot_json", nullable = false, columnDefinition = "text")
    public String statusSnapshotJson;

    @Column(name = "completion_verified_at")
    public Instant completionVerifiedAt;

    @Column(name = "completion_verified_by_user_id")
    public Long completionVerifiedByUserId;

    @Column(name = "completion_verified_by_username", length = 120)
    public String completionVerifiedByUsername;

    @Column(name = "completion_status", length = 32)
    public String completionStatus;

    @Column(name = "completion_message", length = 500)
    public String completionMessage;

    @Column(name = "completion_unsatisfied_check_ids_json", columnDefinition = "text")
    public String completionUnsatisfiedCheckIdsJson;

    @Column(name = "completion_snapshot_json", columnDefinition = "text")
    public String completionSnapshotJson;
}
