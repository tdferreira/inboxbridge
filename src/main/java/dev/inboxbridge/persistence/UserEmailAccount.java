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

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.domain.SourceFetchMode;
import dev.inboxbridge.domain.SourcePostPollAction;

@Entity
@Table(name = "user_email_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_email_account_email_account_id", columnNames = { "email_account_id" })
        },
        indexes = {
                @Index(name = "idx_user_email_account_user", columnList = "user_id"),
                @Index(name = "idx_user_email_account_enabled", columnList = "enabled")
        })
public class UserEmailAccount extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

        @Column(name = "email_account_id", nullable = false, length = 120)
    public String emailAccountId;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 20)
    public InboxBridgeConfig.Protocol protocol;

    @Column(name = "host", nullable = false, length = 255)
    public String host;

    @Column(name = "port", nullable = false)
    public int port;

    @Column(name = "tls", nullable = false)
    public boolean tls;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method", nullable = false, length = 20)
    public InboxBridgeConfig.AuthMethod authMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false, length = 20)
    public InboxBridgeConfig.OAuthProvider oauthProvider;

    @Column(name = "username", nullable = false, length = 255)
    public String username;

    @Column(name = "password_ciphertext", length = 4096)
    public String passwordCiphertext;

    @Column(name = "password_nonce", length = 64)
    public String passwordNonce;

    @Column(name = "oauth_refresh_token_ciphertext", length = 4096)
    public String oauthRefreshTokenCiphertext;

    @Column(name = "oauth_refresh_token_nonce", length = 64)
    public String oauthRefreshTokenNonce;

    @Column(name = "key_version", length = 64)
    public String keyVersion;

    @Column(name = "folder_name", length = 255)
    public String folderName;

    @Column(name = "unread_only", nullable = false)
    public boolean unreadOnly;

    @Enumerated(EnumType.STRING)
    @Column(name = "fetch_mode", nullable = false, length = 20)
    public SourceFetchMode fetchMode;

    @Column(name = "custom_label", length = 255)
    public String customLabel;

    @Column(name = "mark_read_after_poll", nullable = false)
    public boolean markReadAfterPoll;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_poll_action", nullable = false, length = 20)
    public SourcePostPollAction postPollAction;

    @Column(name = "post_poll_target_folder", length = 255)
    public String postPollTargetFolder;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
