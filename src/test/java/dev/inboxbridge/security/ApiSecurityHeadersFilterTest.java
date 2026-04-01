package dev.inboxbridge.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

class ApiSecurityHeadersFilterTest {

    @Test
    void filterAddsHardenedHeadersToApiResponsesAndDisablesProxyBufferingForSse() {
        ApiSecurityHeadersFilter filter = new ApiSecurityHeadersFilter();
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();

        filter.filter(
                requestContext(URI.create("https://app.example.com/api/poll/events"), "api/poll/events"),
                responseContext(headers));

        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("DENY", headers.getFirst("X-Frame-Options"));
        assertEquals("no-referrer", headers.getFirst("Referrer-Policy"));
        assertEquals("no-store, no-cache, must-revalidate", headers.getFirst("Cache-Control"));
        assertEquals("no", headers.getFirst("X-Accel-Buffering"));
        assertEquals("max-age=31536000; includeSubDomains", headers.getFirst("Strict-Transport-Security"));
    }

    @Test
    void filterIgnoresNonApiResponses() {
        ApiSecurityHeadersFilter filter = new ApiSecurityHeadersFilter();
        MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();

        filter.filter(
                requestContext(URI.create("https://app.example.com/"), ""),
                responseContext(headers));

        assertNull(headers.getFirst("X-Content-Type-Options"));
        assertNull(headers.getFirst("Cache-Control"));
    }

    private static ContainerRequestContext requestContext(URI requestUri, String path) {
        UriInfo uriInfo = (UriInfo) Proxy.newProxyInstance(
                UriInfo.class.getClassLoader(),
                new Class<?>[] { UriInfo.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequestUri" -> requestUri;
                    case "getPath" -> path;
                    default -> null;
                });
        return (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class<?>[] { ContainerRequestContext.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUriInfo" -> uriInfo;
                    default -> null;
                });
    }

    private static ContainerResponseContext responseContext(MultivaluedMap<String, Object> headers) {
        return (ContainerResponseContext) Proxy.newProxyInstance(
                ContainerResponseContext.class.getClassLoader(),
                new Class<?>[] { ContainerResponseContext.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHeaders" -> headers;
                    case "getStringHeaders" -> new MultivaluedHashMap<String, String>();
                    case "getAllowedMethods" -> java.util.Set.<String>of();
                    case "getCookies" -> java.util.Map.<String, jakarta.ws.rs.core.NewCookie>of();
                    case "getEntityAnnotations" -> new java.lang.annotation.Annotation[0];
                    case "getLinks" -> java.util.Set.<jakarta.ws.rs.core.Link>of();
                    case "getLength" -> -1;
                    case "hasEntity" -> false;
                    case "getEntityClass" -> Object.class;
                    case "getEntityType" -> Object.class;
                    case "getMediaType" -> null;
                    case "getStatus" -> 200;
                    case "getStatusInfo" -> jakarta.ws.rs.core.Response.Status.OK;
                    case "getEntity" -> null;
                    default -> null;
                });
    }
}
