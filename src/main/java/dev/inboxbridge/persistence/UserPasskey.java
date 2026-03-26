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

/**
 * Durable record of a registered WebAuthn credential for an InboxBridge user.
 *
 * <p>The credential public key is not secret, so it is stored in plaintext to
 * remain queryable by the verifier. Sensitive session state for the ceremony is
 * kept separately and discarded after registration or authentication
 * completes.</p>
 */
@Entity
@Table(name = "user_passkey",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_passkey_credential", columnNames = { "credential_id" })
        },
        indexes = {
                @Index(name = "idx_user_passkey_user", columnList = "user_id")
        })
public class UserPasskey extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "label", nullable = false, length = 160)
    public String label;

    @Column(name = "credential_id", nullable = false, length = 1024)
    public String credentialId;

    @Column(name = "public_key_cose", nullable = false, length = 4096)
    public String publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    public long signatureCount;

    @Column(name = "aaguid", length = 64)
    public String aaguid;

    @Column(name = "transports", length = 512)
    public String transports;

    @Column(name = "discoverable", nullable = false)
    public boolean discoverable;

    @Column(name = "backup_eligible", nullable = false)
    public boolean backupEligible;

    @Column(name = "backed_up", nullable = false)
    public boolean backedUp;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "last_used_at")
    public Instant lastUsedAt;
}
