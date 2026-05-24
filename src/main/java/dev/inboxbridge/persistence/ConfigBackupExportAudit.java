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
@Table(name = "config_backup_export_audit",
        indexes = {
                @Index(name = "idx_config_backup_export_audit_actor", columnList = "actor_user_id"),
                @Index(name = "idx_config_backup_export_audit_created", columnList = "created_at")
        })
public class ConfigBackupExportAudit extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "actor_user_id", nullable = false)
    public Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 255)
    public String actorUsername;

    @Column(name = "export_type", nullable = false, length = 40)
    public String exportType;

    @Column(name = "encrypted", nullable = false)
    public boolean encrypted;

    @Column(name = "public_key_fingerprint", length = 128)
    public String publicKeyFingerprint;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
