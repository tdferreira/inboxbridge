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

import dev.inboxbridge.config.BridgeConfig;

@Entity
@Table(name = "user_bridge",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_bridge_bridge_id", columnNames = { "bridge_id" })
        },
        indexes = {
                @Index(name = "idx_user_bridge_user", columnList = "user_id"),
                @Index(name = "idx_user_bridge_enabled", columnList = "enabled")
        })
public class UserBridge extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "bridge_id", nullable = false, length = 120)
    public String bridgeId;

    @Column(name = "enabled", nullable = false)
    public boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 20)
    public BridgeConfig.Protocol protocol;

    @Column(name = "host", nullable = false, length = 255)
    public String host;

    @Column(name = "port", nullable = false)
    public int port;

    @Column(name = "tls", nullable = false)
    public boolean tls;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_method", nullable = false, length = 20)
    public BridgeConfig.AuthMethod authMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false, length = 20)
    public BridgeConfig.OAuthProvider oauthProvider;

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

    @Column(name = "custom_label", length = 255)
    public String customLabel;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
