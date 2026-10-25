package dev.inboxbridge.service.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;

@ApplicationScoped
public class AuthClientAddressService {

    private static final String DEFAULT_TRUSTED_PROXIES = "127.0.0.1,::1,inboxbridge-admin";

    @ConfigProperty(name = "inboxbridge.security.auth.trusted-proxy-addresses", defaultValue = DEFAULT_TRUSTED_PROXIES)
    String trustedProxyAddresses = DEFAULT_TRUSTED_PROXIES;

    public String resolveClientKey(HttpHeaders headers) {
        return resolveClientKey(headers, null);
    }

    public String resolveClientKey(HttpHeaders headers, String directRemoteAddress) {
        String directAddress = normalizeIp(directRemoteAddress);
        if (!isTrustedProxyHop(directAddress)) {
            return directAddress == null ? "unknown" : directAddress;
        }
        String preferredForwarded = firstNonBlank(
                headerIp(headers, "X-Real-IP"),
                forwardedForClient(headers));
        if (preferredForwarded != null) {
            return preferredForwarded;
        }
        if (directAddress != null) {
            return directAddress;
        }
        return "unknown";
    }

    private String forwardedForClient(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String forwardedFor = headers.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Only the immediate trusted hop's appended value is authoritative.
            return normalizeIp(forwardedFor.substring(forwardedFor.lastIndexOf(',') + 1));
        }
        return null;
    }

    private String headerIp(HttpHeaders headers, String name) {
        if (headers == null || name == null || name.isBlank()) {
            return null;
        }
        return normalizeIp(headers.getHeaderString(name));
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isTrustedProxyHop(String candidate) {
        if (candidate == null) {
            return false;
        }
        for (String configured : trustedProxyAddresses.split(",")) {
            if (configured.isBlank()) {
                continue;
            }
            try {
                // DNS resolution is restricted to operator-configured proxy names.
                for (InetAddress proxy : InetAddress.getAllByName(configured.trim())) {
                    if (candidate.equals(proxy.getHostAddress())) {
                        return true;
                    }
                }
            } catch (UnknownHostException ignored) {
                // An unavailable configured proxy must not broaden trust.
            }
        }
        return false;
    }

    private String normalizeIp(String candidate) {
        if (candidate == null) {
            return null;
        }
        String normalized = candidate.trim();
        if (normalized.isBlank() || normalized.length() > 45 || "unknown".equalsIgnoreCase(normalized)) {
            return null;
        }
        try {
            if (normalized.contains(":") && normalized.matches("[0-9a-fA-F:.]+")) {
                return InetAddress.getByName(normalized).getHostAddress();
            }
            String[] parts = normalized.split("\\.", -1);
            if (parts.length != 4) {
                return null;
            }
            byte[] bytes = new byte[4];
            for (int index = 0; index < parts.length; index++) {
                if (!parts[index].matches("[0-9]{1,3}")) {
                    return null;
                }
                int value = Integer.parseInt(parts[index]);
                if (value > 255) {
                    return null;
                }
                bytes[index] = (byte) value;
            }
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException | NumberFormatException ignored) {
            return null;
        }
    }
}
