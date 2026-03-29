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
@Table(name = "user_mail_destination_config",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_mail_destination_config_user", columnNames = { "user_id" })
        },
        indexes = {
                @Index(name = "idx_user_mail_destination_config_user", columnList = "user_id")
        })
public class UserMailDestinationConfig extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "provider", nullable = false, length = 40)
    public String provider;

    @Column(name = "host", length = 255)
    public String host;

    @Column(name = "port")
    public Integer port;

    @Column(name = "tls", nullable = false)
    public boolean tls;

    @Column(name = "auth_method", nullable = false, length = 20)
    public String authMethod;

    @Column(name = "oauth_provider", nullable = false, length = 20)
    public String oauthProvider;

    @Column(name = "username", length = 255)
    public String username;

    @Column(name = "password_ciphertext", length = 4096)
    public String passwordCiphertext;

    @Column(name = "password_nonce", length = 64)
    public String passwordNonce;

    @Column(name = "folder_name", length = 255)
    public String folderName;

    @Column(name = "key_version", length = 64)
    public String keyVersion;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}