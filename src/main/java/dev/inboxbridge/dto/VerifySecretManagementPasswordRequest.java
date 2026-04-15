package dev.inboxbridge.dto;

/**
 * Password confirmation used to step up an existing browser session before
 * sensitive secret-management operations can execute.
 */
public record VerifySecretManagementPasswordRequest(
        String password) {
}
