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
@Table(name = "app_user",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_app_user_username", columnNames = { "username" })
        },
        indexes = {
                @Index(name = "idx_app_user_role", columnList = "role")
        })
public class AppUser extends PanacheEntityBase {

    public enum Role {
        ADMIN,
        USER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "username", nullable = false, length = 120)
    public String username;

    @Column(name = "user_handle", nullable = false, length = 128)
    public String userHandle;

    @Column(name = "password_hash", length = 512)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    public Role role;

    @Column(name = "must_change_password", nullable = false)
    public boolean mustChangePassword;

    @Column(name = "active", nullable = false)
    public boolean active;

    @Column(name = "approved", nullable = false)
    public boolean approved;

    @Column(name = "disabled_by_single_user_mode", nullable = false)
    public boolean disabledBySingleUserMode;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
