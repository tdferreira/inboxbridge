package dev.inboxbridge.web.oauth;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.ws.rs.core.UriBuilder;

final class OAuthCallbackRedirectBuilder {

    private OAuthCallbackRedirectBuilder() {
    }

    static URI build(String route, Map<String, String> parameters) {
        UriBuilder builder = UriBuilder.fromPath(route);
        parameters.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                builder.queryParam(key, value);
            }
        });
        return builder.build();
    }

    static Map<String, String> parameters(String language, String code, String state, String error, String errorDescription) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("lang", normalizeLanguage(language));
        parameters.put("code", code);
        parameters.put("state", state);
        parameters.put("error", error);
        parameters.put("error_description", errorDescription);
        return parameters;
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        String normalized = language.trim();
        if (normalized.equalsIgnoreCase("pt") || normalized.toLowerCase().startsWith("pt-pt")) {
            return "pt-PT";
        }
        if (normalized.toLowerCase().startsWith("pt-br")) {
            return "pt-BR";
        }
        if (normalized.toLowerCase().startsWith("fr")) {
            return "fr";
        }
        if (normalized.toLowerCase().startsWith("de")) {
            return "de";
        }
        if (normalized.toLowerCase().startsWith("es")) {
            return "es";
        }
        return "en";
    }
}
