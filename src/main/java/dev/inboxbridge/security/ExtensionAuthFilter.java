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

/**
 * Authenticates extension-scoped bearer tokens for the narrow `/api/extension`
 * surface without reusing the main browser-session cookie model.
 */
@Provider
@RequireExtensionAuth
@Priority(Priorities.AUTHENTICATION)
public class ExtensionAuthFilter implements ContainerRequestFilter {

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
            throw new NotAuthorizedException("Not authenticated");
        }

        String rawToken = authorization.substring("Bearer ".length()).trim();
        ExtensionSessionService.AuthenticatedExtensionSession authenticated = extensionSessionService.authenticate(rawToken)
                .orElseThrow(() -> new NotAuthorizedException("Not authenticated"));
        ExtensionSession extensionSession = extensionSessionRepository.findByIdOptional(authenticated.sessionId())
                .orElseThrow(() -> new NotAuthorizedException("Not authenticated"));
        AppUser user = appUserRepository.findByIdOptional(authenticated.userId())
                .filter(candidate -> candidate.active && candidate.approved)
                .orElseThrow(() -> new NotAuthorizedException("Not authenticated"));

        currentUserContext.setUser(user);
        currentUserContext.setExtensionSession(extensionSession);
    }
}
