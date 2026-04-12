package dev.inboxbridge.web.extension;

import dev.inboxbridge.dto.ApiError;
import dev.inboxbridge.dto.ExtensionBrowserAuthCompleteRequest;
import dev.inboxbridge.dto.ExtensionBrowserAuthCompleteResponse;
import dev.inboxbridge.dto.ExtensionBrowserAuthRedeemRequest;
import dev.inboxbridge.dto.ExtensionBrowserAuthRedeemResponse;
import dev.inboxbridge.dto.ExtensionBrowserAuthStartRequest;
import dev.inboxbridge.dto.ExtensionBrowserAuthStartResponse;
import dev.inboxbridge.dto.ExtensionAuthLoginRequest;
import dev.inboxbridge.dto.ExtensionAuthPasskeyVerifyRequest;
import dev.inboxbridge.dto.ExtensionAuthRefreshRequest;
import dev.inboxbridge.dto.ExtensionAuthResponse;
import dev.inboxbridge.dto.ExtensionAuthSessionView;
import dev.inboxbridge.dto.ExtensionAuthTokensView;
import dev.inboxbridge.dto.ExtensionUserView;
import dev.inboxbridge.service.auth.AuthClientAddressService;
import dev.inboxbridge.service.auth.AuthLoginProtectionService;
import dev.inboxbridge.service.auth.AuthService;
import dev.inboxbridge.service.auth.PasskeyService;
import dev.inboxbridge.service.extension.ExtensionBrowserAuthHandoffService;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import dev.inboxbridge.service.oauth.PublicUrlService;
import dev.inboxbridge.service.user.UserUiPreferenceService;
import dev.inboxbridge.security.CurrentUserContext;
import dev.inboxbridge.security.RequireAuth;
import dev.inboxbridge.web.WebResourceSupport;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Lets the browser extension authenticate directly with username/password or
 * passkey, then exchange that identity for extension-scoped rotating tokens.
 */
@Path("/api/extension/auth")
@Produces(MediaType.APPLICATION_JSON)
public class ExtensionAuthResource {
    private static final String ORIGIN_HEADER = "Origin";

    @Inject
    AuthService authService;

    @Inject
    PasskeyService passkeyService;

    @Inject
    ExtensionSessionService extensionSessionService;

    @Inject
    ExtensionBrowserAuthHandoffService extensionBrowserAuthHandoffService;

    @Inject
    PublicUrlService publicUrlService;

    @Inject
    AuthClientAddressService authClientAddressService;

    @Inject
    AuthLoginProtectionService authLoginProtectionService;

    @Inject
    UserUiPreferenceService userUiPreferenceService;

    @Inject
    CurrentUserContext currentUserContext;

    @Context
    HttpHeaders httpHeaders;

    @Context
    HttpServerRequest httpServerRequest;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response login(ExtensionAuthLoginRequest request) {
        String clientKey = authClientAddressService.resolveClientKey(httpHeaders, directRemoteAddress());
        try {
            authLoginProtectionService.requireLoginAllowed(clientKey);
            AuthService.AuthenticationResult result = authService.authenticate(
                    request == null ? null : request.username(),
                    request == null ? null : request.password());
            authLoginProtectionService.recordSuccessfulLogin(clientKey);
            if (result.status() == AuthService.AuthenticationStatus.PASSKEY_REQUIRED) {
                return Response.ok(ExtensionAuthResponse.passkeyRequired(result.passkeyChallenge())).build();
            }
            return Response.ok(ExtensionAuthResponse.authenticated(createSessionView(
                    extensionSessionService.createAuthenticatedSession(
                            result.user(),
                            request == null ? null : request.label(),
                            request == null ? null : request.browserFamily(),
                            request == null ? null : request.extensionVersion()),
                    result.user().username)))
                    .build();
        } catch (AuthLoginProtectionService.LoginBlockedException e) {
            return loginBlockedResponse(e.blockedUntil());
        } catch (IllegalArgumentException e) {
            AuthLoginProtectionService.FailureResult failure = authLoginProtectionService.recordFailedLogin(clientKey);
            if (failure.blocked()) {
                return loginBlockedResponse(failure.blockedUntil());
            }
            throw WebResourceSupport.badRequest(e);
        }
    }

    @POST
    @Path("/passkey/verify")
    @Consumes(MediaType.APPLICATION_JSON)
    public ExtensionAuthResponse finishPasskeyLogin(ExtensionAuthPasskeyVerifyRequest request) {
        return WebResourceSupport.badRequest(() -> {
            PasskeyService.PasskeyAuthenticationResult authResult = passkeyService.finishAuthentication(
                    new dev.inboxbridge.dto.FinishPasskeyCeremonyRequest(
                            request == null ? null : request.ceremonyId(),
                            request == null ? null : request.credentialJson()),
                    extensionRequestOrigins());
            AuthService.AuthenticationResult authenticated = authService.authenticateWithPasskey(authResult);
            return ExtensionAuthResponse.authenticated(createSessionView(
                    extensionSessionService.createAuthenticatedSession(
                            authenticated.user(),
                            request == null ? null : request.label(),
                            request == null ? null : request.browserFamily(),
                            request == null ? null : request.extensionVersion()),
                    authenticated.user().username));
        });
    }

    @POST
    @Path("/refresh")
    @Consumes(MediaType.APPLICATION_JSON)
    public ExtensionAuthResponse refresh(ExtensionAuthRefreshRequest request) {
        return extensionSessionService.refresh(request == null ? null : request.refreshToken())
                .map(this::createSessionView)
                .map(ExtensionAuthResponse::authenticated)
                .orElseThrow(() -> new jakarta.ws.rs.NotAuthorizedException("Not authenticated"));
    }

    @POST
    @Path("/browser-handoff/start")
    @Consumes(MediaType.APPLICATION_JSON)
    public ExtensionBrowserAuthStartResponse startBrowserHandoff(ExtensionBrowserAuthStartRequest request) {
        return WebResourceSupport.badRequest(() -> {
            var started = extensionBrowserAuthHandoffService.start(
                    request == null ? null : request.codeChallenge(),
                    request == null ? null : request.codeChallengeMethod(),
                    request == null ? null : request.label(),
                    request == null ? null : request.browserFamily(),
                    request == null ? null : request.extensionVersion());
            return new ExtensionBrowserAuthStartResponse(
                    started.requestId(),
                    browserHandoffBaseUrl() + "/?extensionAuthRequest=" + started.requestId(),
                    started.expiresAt());
        });
    }

    @POST
    @Path("/browser-handoff/complete")
    @RequireAuth
    @Consumes(MediaType.APPLICATION_JSON)
    public ExtensionBrowserAuthCompleteResponse completeBrowserHandoff(ExtensionBrowserAuthCompleteRequest request) {
        return WebResourceSupport.badRequest(() -> {
            extensionBrowserAuthHandoffService.complete(
                    request == null ? null : request.requestId(),
                    currentUserContext == null ? null : currentUserContext.user());
            return ExtensionBrowserAuthCompleteResponse.completed();
        });
    }

    @POST
    @Path("/browser-handoff/redeem")
    @Consumes(MediaType.APPLICATION_JSON)
    public ExtensionBrowserAuthRedeemResponse redeemBrowserHandoff(ExtensionBrowserAuthRedeemRequest request) {
        return WebResourceSupport.badRequest(() -> {
            var result = extensionBrowserAuthHandoffService.redeem(
                    request == null ? null : request.requestId(),
                    request == null ? null : request.codeVerifier());
            return switch (result.status()) {
                case PENDING -> ExtensionBrowserAuthRedeemResponse.pending(result.expiresAt());
                case AUTHENTICATED -> ExtensionBrowserAuthRedeemResponse.authenticated(
                        createSessionView(result.session()),
                        result.expiresAt());
                case EXPIRED -> ExtensionBrowserAuthRedeemResponse.expired();
            };
        });
    }

    private ExtensionAuthSessionView createSessionView(ExtensionSessionService.CreatedExtensionAuthSession created) {
        return createSessionView(created, null);
    }

    private ExtensionAuthSessionView createSessionView(
            ExtensionSessionService.CreatedExtensionAuthSession created,
            String username) {
        var uiPreference = created.session().userId == null
                ? userUiPreferenceService.defaultView()
                : userUiPreferenceService.viewForUser(created.session().userId)
                        .orElseGet(userUiPreferenceService::defaultView);
        return new ExtensionAuthSessionView(
                created.session().id,
                created.session().label,
                created.session().browserFamily,
                created.session().extensionVersion,
                publicUrlService.publicBaseUrl(),
                new ExtensionUserView(
                        username,
                        username,
                        uiPreference.language(),
                        uiPreference.themeMode()),
                new ExtensionAuthTokensView(
                        created.accessToken(),
                        created.session().accessExpiresAt,
                        created.refreshToken(),
                        created.session().expiresAt));
    }

    private Response loginBlockedResponse(java.time.Instant blockedUntil) {
        return Response.status(429)
                .entity(new ApiError(
                        "auth_login_blocked",
                        "Too many failed sign-in attempts from this address.",
                        "Too many failed sign-in attempts from this address.",
                        java.util.Map.of("blockedUntil", blockedUntil.toString())))
                .build();
    }

    private String directRemoteAddress() {
        if (httpServerRequest == null || httpServerRequest.remoteAddress() == null) {
            return null;
        }
        return httpServerRequest.remoteAddress().hostAddress();
    }

    private java.util.Set<String> extensionRequestOrigins() {
        if (httpHeaders == null) {
            return java.util.Set.of();
        }
        java.util.List<String> origins = httpHeaders.getRequestHeader(ORIGIN_HEADER);
        if (origins == null || origins.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.LinkedHashSet<>(origins);
    }

    private String browserHandoffBaseUrl() {
        String forwardedProto = firstHeaderValue("X-Forwarded-Proto");
        String forwardedHost = firstHeaderValue("X-Forwarded-Host");
        String forwardedPort = firstHeaderValue("X-Forwarded-Port");
        String hostHeader = firstHeaderValue(HttpHeaders.HOST);
        String scheme = forwardedProto;
        if (scheme == null || scheme.isBlank()) {
            scheme = httpServerRequest == null ? null : httpServerRequest.scheme();
        }
        String hostPort = forwardedHost;
        if (hostPort == null || hostPort.isBlank()) {
            hostPort = hostHeader;
        }
        if (scheme == null || scheme.isBlank() || hostPort == null || hostPort.isBlank()) {
            return publicUrlService.publicBaseUrl();
        }
        if (forwardedPort != null && !forwardedPort.isBlank() && !containsExplicitPort(hostPort)) {
            hostPort = hostPort + ":" + forwardedPort;
        }
        return scheme + "://" + hostPort;
    }

    private String firstHeaderValue(String name) {
        if (httpHeaders == null) {
            return null;
        }
        java.util.List<String> values = httpHeaders.getRequestHeader(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String first = values.getFirst();
        if (first == null || first.isBlank()) {
            return null;
        }
        return first.split(",", 2)[0].trim();
    }

    private boolean containsExplicitPort(String hostValue) {
        if (hostValue == null || hostValue.isBlank()) {
            return false;
        }
        int closingBracket = hostValue.lastIndexOf(']');
        int colonIndex = hostValue.lastIndexOf(':');
        return colonIndex > -1 && colonIndex > closingBracket;
    }
}
