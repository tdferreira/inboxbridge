package dev.inboxbridge.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;

@ApplicationScoped
public class AuthClientAddressService {

    public String resolveClientKey(HttpHeaders headers) {
        if (headers == null) {
            return "unknown";
        }
        String forwardedFor = headers.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        String realIp = headers.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return "unknown";
    }
}
