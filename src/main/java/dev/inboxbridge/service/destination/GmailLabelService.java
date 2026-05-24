package dev.inboxbridge.service.destination;

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

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.GmailLabelOptionView;
import dev.inboxbridge.domain.GmailApiDestinationTarget;
import dev.inboxbridge.domain.GmailTarget;
import dev.inboxbridge.domain.SourceFolderLabelMappings;
import dev.inboxbridge.service.oauth.GoogleOAuthService;
import dev.inboxbridge.service.oauth.SystemOAuthAppSettingsService;
import dev.inboxbridge.service.oauth.UserGmailConfigService;
import dev.inboxbridge.service.user.UserMailDestinationConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GmailLabelService {

    @FunctionalInterface
    interface AuthorizedRequestFactory {
        HttpRequest create(String accessToken);
    }

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    SystemOAuthAppSettingsService systemOAuthAppSettingsService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    InboxBridgeConfig config;

    @Inject
    UserGmailConfigService userGmailConfigService;

    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public List<String> resolveLabelIds(Optional<String> customLabel) {
        return resolveLabelIds(systemTarget(), customLabel);
    }

    public List<String> resolveLabelIds(GmailApiDestinationTarget target, Optional<String> customLabel) {
        return resolveLabelIds(target, customLabel, Optional.empty(), Optional.empty());
    }

    public List<String> resolveLabelIds(
            GmailApiDestinationTarget target,
            Optional<String> customLabel,
            Optional<String> folderLabelMappings,
            Optional<String> sourceFolder) {
        return resolveLabelIds(target, customLabel, folderLabelMappings, sourceFolder, false);
    }

    public List<String> resolveLabelIds(
            GmailApiDestinationTarget target,
            Optional<String> customLabel,
            Optional<String> folderLabelMappings,
            Optional<String> sourceFolder,
            boolean routeToSpam) {
        List<String> labelIds = new ArrayList<>();
        labelIds.add(routeToSpam ? "SPAM" : "INBOX");
        labelIds.add("UNREAD");

        customLabel.filter(label -> !label.isBlank())
                .ifPresent(label -> labelIds.add(resolveCustomLabelId(target, label)));
        SourceFolderLabelMappings.parse(folderLabelMappings)
                .labelFor(sourceFolder)
                .ifPresent(label -> {
                    String labelId = resolveCustomLabelId(target, label);
                    if (!labelIds.contains(labelId)) {
                        labelIds.add(labelId);
                    }
                });
        return labelIds;
    }

    public List<String> resolveLabelIds(GmailTarget target, Optional<String> customLabel) {
        return resolveLabelIds(asDestinationTarget(target), customLabel);
    }

    public List<GmailLabelOptionView> listLabelOptions(GmailApiDestinationTarget target) {
        try {
            HttpResponse<String> response = sendAuthorizedRequestWithRetry(
                    target,
                    accessToken -> HttpRequest.newBuilder(URI.create("https://gmail.googleapis.com/gmail/v1/users/" + urlEncode(target.destinationUser()) + "/labels"))
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + accessToken)
                            .GET()
                            .build());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Failed to list Gmail labels: " + response.statusCode() + " - " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            List<GmailLabelOptionView> labels = new ArrayList<>();
            for (JsonNode label : root.withArray("labels")) {
                labels.add(new GmailLabelOptionView(
                        label.path("id").asText(),
                        label.path("name").asText(),
                        label.path("type").asText()));
            }
            labels.sort(java.util.Comparator.comparing(GmailLabelOptionView::name, String.CASE_INSENSITIVE_ORDER));
            return labels;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to list Gmail labels", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list Gmail labels", e);
        }
    }

    public List<String> resolveLabelIds(
            GmailTarget target,
            Optional<String> customLabel,
            Optional<String> folderLabelMappings,
            Optional<String> sourceFolder) {
        return resolveLabelIds(asDestinationTarget(target), customLabel, folderLabelMappings, sourceFolder);
    }

    public List<String> resolveLabelIds(
            GmailTarget target,
            Optional<String> customLabel,
            Optional<String> folderLabelMappings,
            Optional<String> sourceFolder,
            boolean routeToSpam) {
        return resolveLabelIds(asDestinationTarget(target), customLabel, folderLabelMappings, sourceFolder, routeToSpam);
    }

    private String resolveCustomLabelId(GmailApiDestinationTarget target, String labelName) {
        Map<String, String> labelsByName = listLabels(target);
        if (labelsByName.containsKey(labelName)) {
            return labelsByName.get(labelName);
        }
        if (!target.createMissingLabels()) {
            throw new IllegalStateException("Custom label does not exist and auto-create is disabled: " + labelName);
        }
        return createLabel(target, labelName);
    }

    private Map<String, String> listLabels(GmailApiDestinationTarget target) {
        try {
            HttpResponse<String> response = sendAuthorizedRequestWithRetry(
                    target,
                    accessToken -> HttpRequest.newBuilder(URI.create("https://gmail.googleapis.com/gmail/v1/users/" + urlEncode(target.destinationUser()) + "/labels"))
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + accessToken)
                            .GET()
                            .build());
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

    private String createLabel(GmailApiDestinationTarget target, String labelName) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", labelName);
        payload.put("labelListVisibility", "labelShow");
        payload.put("messageListVisibility", "show");
        try {
            HttpResponse<String> response = sendAuthorizedRequestWithRetry(
                    target,
                    accessToken -> HttpRequest.newBuilder(URI.create("https://gmail.googleapis.com/gmail/v1/users/" + urlEncode(target.destinationUser()) + "/labels"))
                            .timeout(Duration.ofSeconds(20))
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                            .build());
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

    private HttpResponse<String> sendAuthorizedRequestWithRetry(
            GmailApiDestinationTarget target,
            AuthorizedRequestFactory requestFactory) throws IOException, InterruptedException {
        HttpResponse<String> response = sendAuthorizedRequest(requestFactory, googleOAuthService.getAccessToken(target.oauthProfile()));
        if (response.statusCode() == 401) {
            googleOAuthService.clearCachedToken(target.oauthProfile().subjectKey());
            response = sendAuthorizedRequest(requestFactory, googleOAuthService.getAccessToken(target.oauthProfile()));
            if (response.statusCode() == 401) {
                userGmailConfigService.markGoogleAccessRevoked(target);
                throw new IllegalStateException(revokedAccessMessage(target));
            }
        }
        return response;
    }

    private String revokedAccessMessage(GmailApiDestinationTarget target) {
        if (target.userId() != null) {
            return "The linked Gmail account no longer grants InboxBridge access. The saved Gmail OAuth link was cleared. Reconnect it from My Destination Mailbox.";
        }
        return "The configured Gmail account no longer grants InboxBridge access. Reconnect Gmail OAuth or update the configured refresh token.";
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

    private GmailApiDestinationTarget systemTarget() {
        GmailApiDestinationTarget target = new GmailApiDestinationTarget(
                "gmail-destination",
                null,
                "system",
                UserMailDestinationConfigService.PROVIDER_GMAIL,
                systemOAuthAppSettingsService.googleDestinationUser(),
                systemOAuthAppSettingsService.googleClientId(),
                systemOAuthAppSettingsService.googleClientSecret(),
                systemOAuthAppSettingsService.googleRefreshToken(),
                systemOAuthAppSettingsService.googleRedirectUri(),
                config.gmail().createMissingLabels(),
                config.gmail().neverMarkSpam(),
                config.gmail().processForCalendar());
        if (target.refreshToken() == null || target.refreshToken().isBlank()) {
            if (systemOAuthAppSettingsService.envManagedGoogleRefreshTokenBlockedByPolicy()) {
                throw new IllegalStateException(
                        "InboxBridge is configured to block env-managed mailbox secrets. Save the Gmail destination refresh token in the admin UI instead of using GMAIL_REFRESH_TOKEN.");
            }
            throw new IllegalStateException(
                    "The configured Gmail destination is incomplete. Reconnect Gmail OAuth or update the configured refresh token.");
        }
        return target;
    }

    private GmailApiDestinationTarget asDestinationTarget(GmailTarget target) {
        return new GmailApiDestinationTarget(
                target.subjectKey(),
                target.userId(),
                target.ownerUsername(),
                UserMailDestinationConfigService.PROVIDER_GMAIL,
                target.destinationUser(),
                target.clientId(),
                target.clientSecret(),
                target.refreshToken(),
                target.redirectUri(),
                target.createMissingLabels(),
                target.neverMarkSpam(),
                target.processForCalendar());
    }
}
