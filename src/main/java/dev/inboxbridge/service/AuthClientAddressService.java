package dev.inboxbridge.service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;

@ApplicationScoped
public class AuthClientAddressService {

    public String resolveClientKey(HttpHeaders headers) {
        return resolveClientKey(headers, null);
    }

    public String resolveClientKey(HttpHeaders headers, String directRemoteAddress) {
        String directAddress = normalizeIp(directRemoteAddress);
        if (!isTrustedProxyHop(directAddress)) {
            return directAddress == null ? "unknown" : directAddress;
        }
        String preferredForwarded = firstNonBlank(
                headerIp(headers, "CF-Connecting-IP"),
                headerIp(headers, "True-Client-IP"),
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
            String[] entries = forwardedFor.split(",");
            for (int index = entries.length - 1; index >= 0; index -= 1) {
                String candidate = normalizeIp(entries[index]);
                if (candidate != null) {
                    return candidate;
                }
            }
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
        try {
            InetAddress address = InetAddress.getByName(candidate);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                return true;
            }
            if (address instanceof Inet6Address) {
                byte[] bytes = address.getAddress();
                return bytes.length > 0 && (bytes[0] & (byte) 0xfe) == (byte) 0xfc;
            }
            return false;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private String normalizeIp(String candidate) {
        if (candidate == null) {
            return null;
        }
        String normalized = candidate.trim();
        if (normalized.isBlank() || "unknown".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }
}
