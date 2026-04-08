package dev.inboxbridge.service.oauth;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Centralizes the browser-facing public URL derivation used by OAuth and
 * passkey-adjacent flows so backend services do not duplicate PUBLIC_* parsing.
 */
@ApplicationScoped
public class PublicUrlService {

    @ConfigProperty(name = "PUBLIC_BASE_URL")
    Optional<String> publicBaseUrl;

    @ConfigProperty(name = "PUBLIC_HOSTNAME", defaultValue = "localhost")
    String publicHostname;

    @ConfigProperty(name = "PUBLIC_PORT", defaultValue = "3000")
    String publicPort;

    public String publicBaseUrl() {
        return publicBaseUrl
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> "https://" + publicHostname + ":" + publicPort);
    }

    public String googleRedirectUri() {
        return publicBaseUrl() + "/api/google-oauth/callback";
    }

    public String microsoftRedirectUri() {
        return publicBaseUrl() + "/api/microsoft-oauth/callback";
    }
}
