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
@Table(name = "user_gmail_config",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_gmail_config_user", columnNames = { "user_id" })
        },
        indexes = {
                @Index(name = "idx_user_gmail_config_user", columnList = "user_id")
        })
public class UserGmailConfig extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "destination_user", nullable = false, length = 255)
    public String destinationUser;

        @Column(name = "linked_mailbox_address", length = 255)
        public String linkedMailboxAddress;

    @Column(name = "client_id_ciphertext", length = 4096)
    public String clientIdCiphertext;

    @Column(name = "client_id_nonce", length = 64)
    public String clientIdNonce;

    @Column(name = "client_secret_ciphertext", length = 4096)
    public String clientSecretCiphertext;

    @Column(name = "client_secret_nonce", length = 64)
    public String clientSecretNonce;

    @Column(name = "refresh_token_ciphertext", length = 4096)
    public String refreshTokenCiphertext;

    @Column(name = "refresh_token_nonce", length = 64)
    public String refreshTokenNonce;

    @Column(name = "key_version", length = 64)
    public String keyVersion;

    @Column(name = "redirect_uri", nullable = false, length = 500)
    public String redirectUri;

    @Column(name = "create_missing_labels", nullable = false)
    public boolean createMissingLabels;

    @Column(name = "never_mark_spam", nullable = false)
    public boolean neverMarkSpam;

    @Column(name = "process_for_calendar", nullable = false)
    public boolean processForCalendar;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
