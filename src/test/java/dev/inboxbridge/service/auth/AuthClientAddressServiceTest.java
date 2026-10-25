package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;

class AuthClientAddressServiceTest {

    @Test
    void prefersDirectAddressWhenRequestDidNotComeThroughTrustedProxy() {
        AuthClientAddressService service = new AuthClientAddressService();

        String resolved = service.resolveClientKey(headers("203.0.113.9", null), "198.51.100.20");

        assertEquals("198.51.100.20", resolved);
    }

    @Test
    void prefersXRealIpFromTrustedProxy() {
        AuthClientAddressService service = new AuthClientAddressService();
        service.trustedProxyAddresses = "172.18.0.2";

        String resolved = service.resolveClientKey(headers("203.0.113.9", "127.0.0.1"), "172.18.0.2");

        assertEquals("127.0.0.1", resolved);
    }

    @Test
    void fallsBackToRightMostForwardedForEntryWhenOnlyThatHeaderIsAvailable() {
        AuthClientAddressService service = new AuthClientAddressService();
        service.trustedProxyAddresses = "172.18.0.2";

        String resolved = service.resolveClientKey(headers("198.51.100.10, 127.0.0.1", null), "172.18.0.2");

        assertEquals("127.0.0.1", resolved);
    }

    @Test
    void doesNotTrustAnArbitraryPrivateNetworkClient() {
        AuthClientAddressService service = new AuthClientAddressService();
        service.trustedProxyAddresses = "127.0.0.1,::1";
        assertEquals("192.168.1.9", service.resolveClientKey(headers("203.0.113.8", "203.0.113.9"), "192.168.1.9"));
    }

    @Test
    void ignoresSpoofableProviderHeadersEvenForATrustedProxy() {
        AuthClientAddressService service = new AuthClientAddressService();
        service.trustedProxyAddresses = "172.18.0.2";
        HttpHeaders request = headers("198.51.100.10", "198.51.100.10");
        request.getRequestHeaders().add("CF-Connecting-IP", "203.0.113.9");
        request.getRequestHeaders().add("True-Client-IP", "203.0.113.8");
        assertEquals("198.51.100.10", service.resolveClientKey(request, "172.18.0.2"));
    }

    @Test
    void rejectsNonIpHeadersAndDoesNotSkipBackToAnAttackerControlledForwardedEntry() {
        AuthClientAddressService service = new AuthClientAddressService();
        service.trustedProxyAddresses = "172.18.0.2";
        assertEquals("172.18.0.2", service.resolveClientKey(headers("203.0.113.9, invalid", "attacker.example"), "172.18.0.2"));
        assertEquals("172.18.0.2", service.resolveClientKey(headers(null, "999.1.1.1"), "172.18.0.2"));
    }

    @Test
    void normalizesIpv6ProxyAndClientAddresses() {
        AuthClientAddressService service = new AuthClientAddressService();
        service.trustedProxyAddresses = "::1";
        assertEquals("2001:db8:0:0:0:0:0:9", service.resolveClientKey(headers(null, "2001:db8::9"), "::1"));
        assertEquals("unknown", service.resolveClientKey(headers(null, "203.0.113.9"), null));
    }

    private HttpHeaders headers(String forwardedFor, String realIp) {
        MultivaluedHashMap<String, String> values = new MultivaluedHashMap<>();
        if (forwardedFor != null) {
            values.add("X-Forwarded-For", forwardedFor);
        }
        if (realIp != null) {
            values.add("X-Real-IP", realIp);
        }
        return new HttpHeaders() {
            @Override
            public java.util.List<String> getRequestHeader(String name) {
                return values.get(name);
            }

            @Override
            public MultivaluedHashMap<String, String> getRequestHeaders() {
                return values;
            }

            @Override
            public String getHeaderString(String name) {
                return values.getFirst(name);
            }

            @Override
            public java.util.List<jakarta.ws.rs.core.MediaType> getAcceptableMediaTypes() {
                return java.util.List.of();
            }

            @Override
            public java.util.List<java.util.Locale> getAcceptableLanguages() {
                return java.util.List.of();
            }

            @Override
            public jakarta.ws.rs.core.MediaType getMediaType() {
                return null;
            }

            @Override
            public java.util.Locale getLanguage() {
                return null;
            }

            @Override
            public java.util.Map<String, jakarta.ws.rs.core.Cookie> getCookies() {
                return java.util.Map.of();
            }

            @Override
            public java.util.Date getDate() {
                return null;
            }

            @Override
            public int getLength() {
                return 0;
            }
        };
    }
}
