package dev.inboxbridge.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "user_session",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_session_token_hash", columnNames = { "token_hash" })
        },
        indexes = {
                @Index(name = "idx_user_session_user", columnList = "user_id"),
                @Index(name = "idx_user_session_expires", columnList = "expires_at"),
                @Index(name = "idx_user_session_revoked", columnList = "revoked_at")
        })
public class UserSession extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "token_hash", nullable = false, length = 128)
    public String tokenHash;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    public Instant lastSeenAt;

    @Column(name = "client_ip", length = 128)
    public String clientIp;

    @Column(name = "location_label", length = 160)
    public String locationLabel;

    @Column(name = "user_agent", length = 512)
    public String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_method", nullable = false, length = 32)
    public LoginMethod loginMethod = LoginMethod.PASSWORD;

    @Column(name = "revoked_at")
    public Instant revokedAt;

    public enum LoginMethod {
        PASSWORD,
        PASSKEY,
        PASSWORD_PLUS_PASSKEY
    }
}
