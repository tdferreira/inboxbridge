package dev.inboxbridge.persistence;

import java.time.Instant;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_oauth_app_settings")
public class SystemOAuthAppSettings extends PanacheEntityBase {

    @Id
    public Long id;

    @Column(name = "google_client_id_ciphertext")
    public String googleClientIdCiphertext;

    @Column(name = "google_client_id_nonce")
    public String googleClientIdNonce;

    @Column(name = "google_client_secret_ciphertext")
    public String googleClientSecretCiphertext;

    @Column(name = "google_client_secret_nonce")
    public String googleClientSecretNonce;

    @Column(name = "microsoft_client_id_ciphertext")
    public String microsoftClientIdCiphertext;

    @Column(name = "microsoft_client_id_nonce")
    public String microsoftClientIdNonce;

    @Column(name = "microsoft_client_secret_ciphertext")
    public String microsoftClientSecretCiphertext;

    @Column(name = "microsoft_client_secret_nonce")
    public String microsoftClientSecretNonce;

    @Column(name = "google_destination_user", length = 255)
    public String googleDestinationUser;

    @Column(name = "google_redirect_uri", length = 1024)
    public String googleRedirectUri;

    @Column(name = "google_refresh_token_ciphertext")
    public String googleRefreshTokenCiphertext;

    @Column(name = "google_refresh_token_nonce")
    public String googleRefreshTokenNonce;

    @Column(name = "key_version", nullable = false, length = 32)
    public String keyVersion;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "multi_user_enabled_override")
    public Boolean multiUserEnabledOverride;
}
