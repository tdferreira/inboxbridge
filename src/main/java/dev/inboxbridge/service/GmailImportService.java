package dev.inboxbridge.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.inboxbridge.config.BridgeConfig;
import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.dto.GmailImportResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GmailImportService {

    @FunctionalInterface
    interface AuthorizedRequestFactory {
        HttpRequest create(String accessToken);
    }

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    BridgeConfig config;

    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public GmailImportResponse importMessage(byte[] rawMessage, List<String> labelIds) {
        return importMessage(systemTarget(), rawMessage, labelIds);
    }

    public GmailImportResponse importMessage(GmailTarget target, byte[] rawMessage, List<String> labelIds) {
        String encodedMessage = Base64.getUrlEncoder().withoutPadding().encodeToString(rawMessage);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("raw", encodedMessage);
        ArrayNode labelsNode = payload.putArray("labelIds");
        labelIds.forEach(labelsNode::add);

        String uri = "https://gmail.googleapis.com/gmail/v1/users/" + urlEncode(target.destinationUser()) + "/messages/import"
                + "?internalDateSource=dateHeader"
                + "&neverMarkSpam=" + target.neverMarkSpam()
                + "&processForCalendar=" + target.processForCalendar();
        try {
            HttpResponse<String> response = sendAuthorizedRequestWithRetry(
                    target,
                    accessToken -> HttpRequest.newBuilder(URI.create(uri))
                            .timeout(Duration.ofSeconds(45))
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                            .build());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to import Gmail message: " + response.statusCode() + " - " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return new GmailImportResponse(root.path("id").asText(), root.path("threadId").asText(null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to import Gmail message", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to import Gmail message", e);
        }
    }

    private HttpResponse<String> sendAuthorizedRequestWithRetry(
            GmailTarget target,
            AuthorizedRequestFactory requestFactory) throws IOException, InterruptedException {
        HttpResponse<String> response = sendAuthorizedRequest(requestFactory, googleOAuthService.getAccessToken(target.oauthProfile()));
        if (response.statusCode() == 401) {
            googleOAuthService.clearCachedToken(target.oauthProfile().subjectKey());
            response = sendAuthorizedRequest(requestFactory, googleOAuthService.getAccessToken(target.oauthProfile()));
        }
        return response;
    }

    private HttpResponse<String> sendAuthorizedRequest(
            AuthorizedRequestFactory requestFactory,
            String accessToken) throws IOException, InterruptedException {
        return httpClient.send(
                requestFactory.create(accessToken),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private GmailTarget systemTarget() {
        return new GmailTarget(
                "gmail-destination",
                null,
                "system",
                config.gmail().destinationUser(),
                config.gmail().clientId(),
                config.gmail().clientSecret(),
                config.gmail().refreshToken(),
                config.gmail().redirectUri(),
                config.gmail().createMissingLabels(),
                config.gmail().neverMarkSpam(),
                config.gmail().processForCalendar());
    }
}
