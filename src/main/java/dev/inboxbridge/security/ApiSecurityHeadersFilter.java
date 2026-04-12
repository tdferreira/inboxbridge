package dev.inboxbridge.security;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class ApiSecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String path = requestContext.getUriInfo().getPath();
        if (path == null || !path.startsWith("api/")) {
            return;
        }
        responseContext.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        responseContext.getHeaders().putSingle("X-Frame-Options", "DENY");
        responseContext.getHeaders().putSingle("Referrer-Policy", "no-referrer");
        responseContext.getHeaders().putSingle("Cache-Control", "no-store, no-cache, must-revalidate");
        responseContext.getHeaders().putSingle("Pragma", "no-cache");
        if ("https".equalsIgnoreCase(requestContext.getUriInfo().getRequestUri().getScheme())) {
            responseContext.getHeaders().putSingle("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        if (path.startsWith("api/poll/events")
                || path.startsWith("api/admin/poll/events")
                || path.startsWith("api/remote/poll/events")
                || path.startsWith("api/extension/events")) {
            responseContext.getHeaders().putSingle("X-Accel-Buffering", "no");
        }
    }
}
