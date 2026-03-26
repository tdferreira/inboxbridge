package dev.inboxbridge.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Short-lived WebAuthn ceremony state persisted in PostgreSQL so registration
 * and authentication can survive reverse proxies and multi-instance
 * deployments.
 */
@Entity
@Table(name = "passkey_ceremony",
        indexes = {
                @Index(name = "idx_passkey_ceremony_user", columnList = "user_id"),
                @Index(name = "idx_passkey_ceremony_expires", columnList = "expires_at")
        })
public class PasskeyCeremony extends PanacheEntityBase {

    public enum CeremonyType {
        REGISTRATION,
        AUTHENTICATION
    }

    @Id
    @Column(name = "id", nullable = false, length = 64)
    public String id;

    @Column(name = "user_id")
    public Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ceremony_type", nullable = false, length = 32)
    public CeremonyType ceremonyType;

    @Column(name = "request_json", nullable = false, columnDefinition = "TEXT")
    public String requestJson;

    @Column(name = "label", length = 160)
    public String label;

    @Column(name = "password_verified", nullable = false)
    public boolean passwordVerified;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;
}
