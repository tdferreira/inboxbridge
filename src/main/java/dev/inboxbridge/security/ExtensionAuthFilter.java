package dev.inboxbridge.security;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.ExtensionSessionRepository;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Authenticates extension-scoped bearer tokens for the narrow `/api/extension`
 * surface without reusing the main browser-session cookie model.
 */
@Provider
@RequireExtensionAuth
@Priority(Priorities.AUTHENTICATION)
public class ExtensionAuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(ExtensionAuthFilter.class);

    @Inject
    ExtensionSessionService extensionSessionService;

    @Inject
    ExtensionSessionRepository extensionSessionRepository;

    @Inject
    AppUserRepository appUserRepository;

    @Inject
    CurrentUserContext currentUserContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String authorization = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            LOG.warnf("Rejected browser-extension request without bearer token path=%s method=%s",
                    requestContext.getUriInfo().getPath(),
                    requestContext.getMethod());
            throw new NotAuthorizedException("Not authenticated");
        }

        String rawToken = authorization.substring("Bearer ".length()).trim();
        ExtensionSessionService.AuthenticatedExtensionSession authenticated = extensionSessionService.authenticate(rawToken)
                .orElseThrow(() -> {
                    LOG.warnf("Rejected browser-extension request with invalid bearer token path=%s method=%s",
                            requestContext.getUriInfo().getPath(),
                            requestContext.getMethod());
                    return new NotAuthorizedException("Not authenticated");
                });
        ExtensionSession extensionSession = extensionSessionRepository.findByIdOptional(authenticated.sessionId())
                .orElseThrow(() -> {
                    LOG.warnf("Rejected browser-extension request because session id=%s no longer exists path=%s method=%s",
                            authenticated.sessionId(),
                            requestContext.getUriInfo().getPath(),
                            requestContext.getMethod());
                    return new NotAuthorizedException("Not authenticated");
                });
        AppUser user = appUserRepository.findByIdOptional(authenticated.userId())
                .filter(candidate -> candidate.active && candidate.approved)
                .orElseThrow(() -> {
                    LOG.warnf("Rejected browser-extension request because user id=%s is missing, inactive, or unapproved path=%s method=%s sessionId=%s",
                            authenticated.userId(),
                            requestContext.getUriInfo().getPath(),
                            requestContext.getMethod(),
                            authenticated.sessionId());
                    return new NotAuthorizedException("Not authenticated");
                });

        currentUserContext.setUser(user);
        currentUserContext.setExtensionSession(extensionSession);
    }
}
