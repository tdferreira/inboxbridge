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
 * Stores non-sensitive per-user admin-ui layout preferences so the interface
 * can remember collapsible section state across login sessions when the user
 * explicitly opts into that behavior.
 */
@Entity
@Table(name = "user_ui_preference",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_ui_preference_user", columnNames = { "user_id" })
        },
        indexes = {
                @Index(name = "idx_user_ui_preference_user", columnList = "user_id")
        })
public class UserUiPreference extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_id", nullable = false)
    public Long userId;

    @Column(name = "persist_layout", nullable = false)
    public boolean persistLayout;

    @Column(name = "quick_setup_collapsed", nullable = false)
    public boolean quickSetupCollapsed;

    @Column(name = "gmail_destination_collapsed", nullable = false)
    public boolean gmailDestinationCollapsed;

    @Column(name = "source_bridges_collapsed", nullable = false)
    public boolean sourceBridgesCollapsed;

    @Column(name = "system_dashboard_collapsed", nullable = false)
    public boolean systemDashboardCollapsed;

    @Column(name = "user_management_collapsed", nullable = false)
    public boolean userManagementCollapsed;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
