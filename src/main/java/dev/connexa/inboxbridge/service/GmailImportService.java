package dev.connexa.inboxbridge.service;

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

import dev.connexa.inboxbridge.config.BridgeConfig;
import dev.connexa.inboxbridge.dto.GmailImportResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GmailImportService {

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    BridgeConfig config;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public GmailImportResponse importMessage(byte[] rawMessage, List<String> labelIds) {
        String encodedMessage = Base64.getUrlEncoder().withoutPadding().encodeToString(rawMessage);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("raw", encodedMessage);
        ArrayNode labelsNode = payload.putArray("labelIds");
        labelIds.forEach(labelsNode::add);

        String accessToken = googleOAuthService.getAccessToken();
        String uri = "https://gmail.googleapis.com/gmail/v1/users/" + urlEncode(config.gmail().destinationUser()) + "/messages/import"
                + "?internalDateSource=dateHeader"
                + "&neverMarkSpam=" + config.gmail().neverMarkSpam()
                + "&processForCalendar=" + config.gmail().processForCalendar();

        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
