package dev.inboxbridge.dto;

/**
 * Summarizes how many stored secret-bearing records still depend on one
 * concrete key version.
 */
public record SecretManagementKeyUsageView(
        String keyVersion,
        long recordCount,
        String areas,
        boolean active,
        boolean availableForDecryption) {
}
