package dev.inboxbridge.web;

public final class ApiErrorCodes {

    private ApiErrorCodes() {
    }

    public static String resolve(String message, int status) {
        if (message == null || message.isBlank()) {
            return status == 403 ? "forbidden" : "bad_request";
        }
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        if (message.equals("Invalid username or password")) return "auth_invalid_credentials";
        if (message.equals("Too many failed sign-in attempts from this address.")) return "auth_login_blocked";
        if (message.equals("Not authenticated")) return "auth_not_authenticated";
        if (message.equals("Current password is incorrect")) return "account_current_password_incorrect";
        if (message.equals("This account does not have a password configured.")) return "account_password_not_configured";
        if (message.equals("Register a passkey before removing the password.")) return "account_register_passkey_first";
        if (message.equals("New password confirmation does not match")) return "password_confirmation_mismatch";
        if (message.equals("Username already exists")) return "username_already_exists";
        if (message.equals("Unknown user id")) return "user_unknown";
        if (message.equals("Mail fetcher ID already exists")) return "mail_fetcher_id_exists";
        if (message.equals("A source mailbox cannot be the same as My Destination Mailbox. Choose a different mailbox or keep that source disabled.")) return "source_destination_mailbox_conflict";
        if (message.equals("Password is required")) return "password_required";
        if (message.equals("Registration challenge answer is required")) return "registration_challenge_required";
        if (message.equals("Registration challenge is invalid or expired")) return "registration_challenge_invalid";
        if (message.equals("Registration challenge answer is incorrect")) return "registration_challenge_incorrect";
        if (message.equals("OAuth refresh token is required or connect provider OAuth first")) return "oauth_refresh_token_required";
        if (message.equals("Source-side message actions are only supported for IMAP accounts")) return "source_post_poll_actions_imap_only";
        if (message.equals("A target folder is required when moving source messages after polling")) return "source_post_poll_target_required";
        if (message.equals("A target folder can only be set when the post-poll action is Move")) return "source_post_poll_target_move_only";
        if (message.equals("Fetch window override must be between 1 and 500 messages")) return "fetch_window_invalid";
        if (message.equals("Poll interval is required")) return "poll_interval_required";
        if (message.equals("Poll interval must be at least 5 seconds")) return "poll_interval_too_short";
        if (message.startsWith("Unsupported poll interval format.")) return "poll_interval_format_invalid";
        if (message.startsWith("Unsupported poll interval unit.")) return "poll_interval_unit_invalid";
        if (message.equals("The \"from\" date-time is required")) return "date_range_from_required";
        if (message.startsWith("Invalid ISO-8601 date-time:")) return "date_range_invalid";
        if (message.equals("Multi-user mode is disabled for this deployment.")) return "multi_user_disabled";
        if (message.equals("Admin access required") || message.equals("Admin access is required")) return "admin_access_required";
        if (message.equals("Admin access is required for environment-managed email accounts")) return "admin_access_required";
        if (message.equals("You do not have access to that email account")) return "bridge_access_denied";
        if (message.equals("Unknown source id") || message.startsWith("Unknown source id:")) return "source_unknown";
        if (message.equals("Missing OAuth state")) return "oauth_state_missing";
        if (message.equals("Invalid or expired OAuth state")) return "oauth_state_invalid_or_expired";
        if (message.equals("Microsoft OAuth client id is not configured")) return "microsoft_oauth_client_id_missing";
        if (message.equals("Microsoft OAuth client secret is not configured")) return "microsoft_oauth_client_secret_missing";
        if (message.startsWith("Secure token storage is required before completing ")) return "secure_token_storage_required";
        if (message.startsWith("Secure token storage is not configured.")) return "secure_token_storage_not_configured";
        if (message.equals("Only admins can override advanced Gmail account settings from the admin UI.")) return "gmail_admin_override_only";
        if (message.equals("This account requires password verification before passkey sign-in.")) return "passkey_password_verification_required";
        if (message.equals("This account does not have a passkey configured.")) return "passkey_not_configured";
        if (message.equals("This account requires password verification before passkey sign-in.")
                || message.equals("Password validation is required before passkey sign-in for this account.")) {
            return "passkey_password_verification_required";
        }
        if (message.equals("Passkey sign-in failed")) return "passkey_signin_failed";
        if (message.startsWith("Passkey registration failed:")) return "passkey_registration_failed";
        if (message.startsWith("Unable to start passkey registration:")) return "passkey_registration_start_failed";
        if (message.startsWith("Unable to start passkey sign-in:")) return "passkey_signin_start_failed";
        if (message.equals("That passkey belongs to a different user.")
                || message.equals("This passkey registration belongs to a different user.")) {
            return "passkey_wrong_user";
        }
        if (message.equals("You cannot remove the only passkey from a passwordless account.")) return "passkey_remove_last_forbidden";
        if (message.equals("Passkeys are disabled for this InboxBridge deployment.")) return "passkeys_disabled";
        if (message.equals("Password must contain at least 8 characters")) return "password_too_short";
        if (message.equals("New password must be different from the current password")) return "password_must_differ";
        if (message.equals("Password must contain at least one uppercase letter")) return "password_missing_uppercase";
        if (message.equals("Password must contain at least one lowercase letter")) return "password_missing_lowercase";
        if (message.equals("Password must contain at least one number")) return "password_missing_number";
        if (message.equals("Password must contain at least one special character")) return "password_missing_special";
        if (message.equals("Admins cannot remove their own admin rights.")) return "admin_remove_own_role_forbidden";
        if (message.equals("Admins cannot delete their own account.")) return "admin_delete_self_forbidden";
        if (message.equals("At least one approved active admin must remain.")) return "last_active_admin_required";
        if (normalized.contains("authenticate failed")
                || normalized.contains("authenticationfailed")
                || normalized.contains("login failed")
                || normalized.contains("invalid credentials")
                || normalized.contains("logon failure")) {
            return "mail_authentication_failed";
        }
        return status == 403 ? "forbidden" : "bad_request";
    }
}
