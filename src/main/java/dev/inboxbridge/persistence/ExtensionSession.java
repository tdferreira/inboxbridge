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

@Entity
@Table(name = "extension_session",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_extension_session_token_hash", columnNames = { "token_hash" })
        },
        indexes = {
                @Index(name = "idx_extension_session_user", columnList = "user_id"),
                @Index(name = "idx_extension_session_revoked", columnList = "revoked_at")
        })
public class ExtensionSession extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "label", nullable = false, length = 120)
    public String label;

    @Column(name = "browser_family", nullable = false, length = 32)
    public String browserFamily;

    @Column(name = "extension_version", nullable = false, length = 32)
    public String extensionVersion;

    @Column(name = "token_hash", nullable = false, length = 128)
    public String tokenHash;

    @Column(name = "token_prefix", nullable = false, length = 24)
    public String tokenPrefix;

    @Column(name = "access_expires_at")
    public Instant accessExpiresAt;

    @Column(name = "refresh_token_hash", length = 128)
    public String refreshTokenHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_used_at")
    public Instant lastUsedAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;

    public boolean active(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean accessTokenActive(Instant now) {
        return active(now) && (accessExpiresAt == null || accessExpiresAt.isAfter(now));
    }
}
