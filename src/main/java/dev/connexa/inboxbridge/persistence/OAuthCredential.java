package dev.connexa.inboxbridge.persistence;

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

/**
 * Durable encrypted OAuth credential record for a provider/subject pair.
 */
@Entity
@Table(name = "oauth_credential",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_oauth_credential_provider_subject", columnNames = { "provider", "subject_key" })
        },
        indexes = {
                @Index(name = "idx_oauth_credential_provider", columnList = "provider"),
                @Index(name = "idx_oauth_credential_access_expires", columnList = "access_expires_at")
        })
public class OAuthCredential extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "provider", nullable = false, length = 50)
    public String provider;

    @Column(name = "subject_key", nullable = false, length = 200)
    public String subjectKey;

    @Column(name = "key_version", nullable = false, length = 40)
    public String keyVersion;

    @Column(name = "refresh_token_ciphertext", length = 4096)
    public String refreshTokenCiphertext;

    @Column(name = "refresh_token_nonce", length = 64)
    public String refreshTokenNonce;

    @Column(name = "access_token_ciphertext", length = 4096)
    public String accessTokenCiphertext;

    @Column(name = "access_token_nonce", length = 64)
    public String accessTokenNonce;

    @Column(name = "access_expires_at")
    public Instant accessExpiresAt;

    @Column(name = "token_scope", length = 2000)
    public String tokenScope;

    @Column(name = "token_type", length = 100)
    public String tokenType;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "last_refreshed_at")
    public Instant lastRefreshedAt;
}
