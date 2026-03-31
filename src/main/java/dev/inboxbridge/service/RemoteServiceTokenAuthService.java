package dev.inboxbridge.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.persistence.AppUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RemoteServiceTokenAuthService {

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    AppUserService appUserService;

    public Optional<AppUser> authenticate(String authorizationHeader) {
        String configuredToken = inboxBridgeConfig.security().remote().serviceToken()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(null);
        String configuredUsername = inboxBridgeConfig.security().remote().serviceUsername()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(null);
        String presentedToken = bearerToken(authorizationHeader);
        if (configuredToken == null || configuredUsername == null || presentedToken == null) {
            return Optional.empty();
        }
        if (!constantTimeEquals(sha256(configuredToken), sha256(presentedToken))) {
            return Optional.empty();
        }
        return appUserService.findByUsername(configuredUsername)
                .filter(user -> user.active && user.approved);
    }

    private String bearerToken(String header) {
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7) || trimmed.length() <= 7) {
            return null;
        }
        return trimmed.substring(7).trim();
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left == null ? new byte[0] : left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right == null ? new byte[0] : right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Remote service token hashing failed", e);
        }
    }
}
