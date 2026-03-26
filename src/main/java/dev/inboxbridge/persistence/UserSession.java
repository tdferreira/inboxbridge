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
@Table(name = "user_session",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_session_token_hash", columnNames = { "token_hash" })
        },
        indexes = {
                @Index(name = "idx_user_session_user", columnList = "user_id"),
                @Index(name = "idx_user_session_expires", columnList = "expires_at")
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
}
