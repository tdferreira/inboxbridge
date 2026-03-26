package dev.connexa.inboxbridge.web;

import java.util.Map;

import dev.connexa.inboxbridge.dto.GoogleOAuthCodeRequest;
import dev.connexa.inboxbridge.dto.GoogleTokenResponse;
import dev.connexa.inboxbridge.dto.OAuthUrlResponse;
import dev.connexa.inboxbridge.service.GoogleOAuthService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/google-oauth")
@Produces(MediaType.APPLICATION_JSON)
public class GoogleOAuthResource {

    @Inject
    GoogleOAuthService googleOAuthService;

    @GET
    @Path("/url")
    public OAuthUrlResponse authorizationUrl() {
        return new OAuthUrlResponse(googleOAuthService.buildAuthorizationUrl());
    }

    @POST
    @Path("/exchange")
    @Consumes(MediaType.APPLICATION_JSON)
    public GoogleTokenResponse exchange(GoogleOAuthCodeRequest request) {
        return googleOAuthService.exchangeAuthorizationCode(request.code());
    }

    @GET
    @Path("/callback")
    public Map<String, String> callback(@QueryParam("code") String code) {
        return Map.of(
                "message", "Copy the authorization code and POST it to /api/google-oauth/exchange, or wire this endpoint to store tokens.",
                "code", code == null ? "" : code);
    }
}
