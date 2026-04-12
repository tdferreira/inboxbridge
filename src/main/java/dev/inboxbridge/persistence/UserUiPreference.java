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

    @Column(name = "layout_edit_enabled", nullable = false)
    public boolean layoutEditEnabled;

    @Column(name = "quick_setup_collapsed", nullable = false)
    public boolean quickSetupCollapsed;

    @Column(name = "quick_setup_dismissed", nullable = false)
    public boolean quickSetupDismissed;

    @Column(name = "quick_setup_pinned_visible", nullable = false)
    public boolean quickSetupPinnedVisible;

    @Column(name = "admin_quick_setup_dismissed", nullable = false)
    public boolean adminQuickSetupDismissed;

    @Column(name = "admin_quick_setup_pinned_visible", nullable = false)
    public boolean adminQuickSetupPinnedVisible;

    @Column(name = "destination_mailbox_collapsed", nullable = false)
    public boolean destinationMailboxCollapsed;

    @Column(name = "user_polling_collapsed", nullable = false)
    public boolean userPollingCollapsed;

    @Column(name = "user_stats_collapsed", nullable = false)
    public boolean userStatsCollapsed;

    @Column(name = "source_email_accounts_collapsed", nullable = false)
    public boolean sourceEmailAccountsCollapsed;

    @Column(name = "admin_quick_setup_collapsed", nullable = false)
    public boolean adminQuickSetupCollapsed;

    @Column(name = "system_dashboard_collapsed", nullable = false)
    public boolean systemDashboardCollapsed;

    @Column(name = "oauth_apps_collapsed", nullable = false)
    public boolean oauthAppsCollapsed;

    @Column(name = "global_stats_collapsed", nullable = false)
    public boolean globalStatsCollapsed;

    @Column(name = "user_management_collapsed", nullable = false)
    public boolean userManagementCollapsed;

    @Column(name = "user_section_order", nullable = false, length = 255)
    public String userSectionOrder;

    @Column(name = "admin_section_order", nullable = false, length = 255)
    public String adminSectionOrder;

    @Column(name = "language", nullable = false, length = 32)
    public String language;

    @Column(name = "theme_mode", nullable = false, length = 16)
    public String themeMode;

    @Column(name = "date_format", nullable = false, length = 64)
    public String dateFormat;

    @Column(name = "timezone_mode", nullable = false, length = 16)
    public String timezoneMode;

    @Column(name = "timezone", nullable = false, length = 128)
    public String timezone;

    @Column(name = "notification_history", nullable = false, columnDefinition = "TEXT")
    public String notificationHistory;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
