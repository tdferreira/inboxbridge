package dev.connexa.inboxbridge.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.connexa.inboxbridge.config.BridgeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GmailLabelService {

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    BridgeConfig config;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public List<String> resolveLabelIds(Optional<String> customLabel) {
        List<String> labelIds = new ArrayList<>();
        labelIds.add("INBOX");
        labelIds.add("UNREAD");

        customLabel.filter(label -> !label.isBlank())
                .ifPresent(label -> labelIds.add(resolveCustomLabelId(label)));
        return labelIds;
    }

    private String resolveCustomLabelId(String labelName) {
        Map<String, String> labelsByName = listLabels();
        if (labelsByName.containsKey(labelName)) {
            return labelsByName.get(labelName);
        }
        if (!config.gmail().createMissingLabels()) {
            throw new IllegalStateException("Custom label does not exist and auto-create is disabled: " + labelName);
        }
        return createLabel(labelName);
    }

    private Map<String, String> listLabels() {
        String accessToken = googleOAuthService.getAccessToken();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://gmail.googleapis.com/gmail/v1/users/" + urlEncode(config.gmail().destinationUser()) + "/labels"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to list Gmail labels: " + response.statusCode() + " - " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            Map<String, String> labels = new HashMap<>();
            for (JsonNode label : root.withArray("labels")) {
                labels.put(label.path("name").asText(), label.path("id").asText());
            }
            return labels;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to list Gmail labels", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list Gmail labels", e);
        }
    }

    private String createLabel(String labelName) {
        String accessToken = googleOAuthService.getAccessToken();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", labelName);
        payload.put("labelListVisibility", "labelShow");
        payload.put("messageListVisibility", "show");

        HttpRequest request = HttpRequest.newBuilder(URI.create("https://gmail.googleapis.com/gmail/v1/users/" + urlEncode(config.gmail().destinationUser()) + "/labels"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to create Gmail label: " + response.statusCode() + " - " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("id").asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to create Gmail label", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Gmail label", e);
        }
    }
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
