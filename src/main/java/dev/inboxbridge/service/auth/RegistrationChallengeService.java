package dev.inboxbridge.service.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Locale;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.altcha.altcha.v2.Altcha;
import org.altcha.altcha.v2.Altcha.Challenge;
import org.altcha.altcha.v2.Altcha.CreateChallengeOptions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.RegistrationChallengeResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RegistrationChallengeService {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_CAPTCHA_TOKEN_LENGTH = 16_384;
    private static final int MAX_CONSUMED_CHALLENGES = 10_000;

    @Inject
    AuthSecuritySettingsService authSecuritySettingsService;

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    @Inject
    ObjectMapper objectMapper;

    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_REQUEST_TIMEOUT)
            .build();

    private final String altchaHmacKey = UUID.randomUUID().toString();
    private final ConcurrentHashMap<String, Long> usedCaptchaPayloads = new ConcurrentHashMap<>();

    public boolean enabled() {
        return authSecuritySettingsService.effectiveSettings().registrationChallengeEnabled();
    }

    public RegistrationChallengeResponse currentChallenge() {
        if (!enabled()) {
            return RegistrationChallengeResponse.disabled();
        }
        AuthSecuritySettingsService.EffectiveAuthSecuritySettings settings = authSecuritySettingsService.effectiveSettings();
        RegistrationCaptchaProvider provider = effectiveProvider(settings);
        validateProviderConfigured(provider, settings);
        return switch (provider) {
            case ALTCHA -> issueAltchaChallenge(settings);
            case TURNSTILE -> new RegistrationChallengeResponse(true, provider.name(), settings.registrationTurnstileSiteKey(), null);
            case HCAPTCHA -> new RegistrationChallengeResponse(true, provider.name(), settings.registrationHcaptchaSiteKey(), null);
        };
    }

    public void validateAndConsume(String captchaToken, String remoteIpAddress) {
        if (!enabled()) {
            return;
        }
        if (captchaToken == null || captchaToken.isBlank() || captchaToken.length() > MAX_CAPTCHA_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Registration CAPTCHA validation is required");
        }
        AuthSecuritySettingsService.EffectiveAuthSecuritySettings settings = authSecuritySettingsService.effectiveSettings();
        RegistrationCaptchaProvider provider = effectiveProvider(settings);
        validateProviderConfigured(provider, settings);
        switch (provider) {
            case ALTCHA -> validateAltcha(captchaToken.trim());
            case TURNSTILE -> validateTurnstile(captchaToken.trim(), remoteIpAddress, settings);
            case HCAPTCHA -> validateHcaptcha(captchaToken.trim(), remoteIpAddress, settings);
        }
    }

    private RegistrationChallengeResponse issueAltchaChallenge(AuthSecuritySettingsService.EffectiveAuthSecuritySettings settings) {
        InboxBridgeConfig.Security.Auth.RegistrationCaptcha.Altcha config =
                inboxBridgeConfig.security().auth().registrationCaptcha().altcha();
        CreateChallengeOptions options = new CreateChallengeOptions()
                .algorithm(config.algorithm())
                .cost(config.cost())
                .keyLength(config.keyLength())
                .keyPrefix(config.keyPrefix())
                .hmacSignatureSecret(effectiveAltchaHmacKey())
                .expiresInSeconds(Math.max(1L, settings.registrationChallengeTtl().toSeconds()));
        Challenge challenge;
        try {
            challenge = Altcha.createChallenge(options);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create registration CAPTCHA challenge", e);
        }
        return new RegistrationChallengeResponse(
                true,
                RegistrationCaptchaProvider.ALTCHA.name(),
                null,
                new RegistrationChallengeResponse.AltchaChallengeResponse(
                        UUID.randomUUID().toString(),
                        new RegistrationChallengeResponse.AltchaChallengeParametersResponse(
                                challenge.parameters().algorithm(),
                                challenge.parameters().nonce(),
                                challenge.parameters().salt(),
                                challenge.parameters().cost(),
                                challenge.parameters().keyLength(),
                                challenge.parameters().keyPrefix(),
                                challenge.parameters().expiresAt()),
                        challenge.signature()));
    }

    private void validateAltcha(String captchaToken) {
        purgeUsedAltchaPayloads();
        String replayKey;
        long expiresAt;
        try {
            Altcha.Payload payload = Altcha.parsePayload(captchaToken);
            Challenge challenge = payload.challenge();
            if (challenge == null || challenge.parameters() == null || challenge.signature() == null
                    || challenge.signature().isBlank() || challenge.parameters().nonce() == null
                    || challenge.parameters().expiresAt() == null) {
                throw new IllegalArgumentException("Missing signed CAPTCHA parameters");
            }
            Altcha.VerifySolutionResult result = Altcha.verifySolution(
                    challenge,
                    payload.solution(),
                    effectiveAltchaHmacKey(),
                    Altcha.kdf(inboxBridgeConfig.security().auth().registrationCaptcha().altcha().algorithm()));
            double expiryMillis = challenge.parameters().expiresAt().doubleValue() * 1000d;
            if (!result.verified() || !Double.isFinite(expiryMillis) || expiryMillis <= System.currentTimeMillis()
                    || expiryMillis >= Long.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid CAPTCHA solution or expiry");
            }
            expiresAt = (long) Math.ceil(expiryMillis);
            // Consume the signed challenge nonce, not attacker-controlled JSON/base64 formatting.
            replayKey = nonceKey(challenge.parameters().nonce());
        } catch (Exception e) {
            throw new IllegalArgumentException("Registration CAPTCHA is invalid or expired", e);
        }
        synchronized (usedCaptchaPayloads) {
            if (usedCaptchaPayloads.containsKey(replayKey)) {
                throw new IllegalArgumentException("Registration CAPTCHA is invalid or already used");
            }
            if (usedCaptchaPayloads.size() >= MAX_CONSUMED_CHALLENGES) {
                throw new IllegalArgumentException("Too many registration challenges. Try again later.");
            }
            // Keep the entry until the signed expiry, even if the configured TTL changes.
            usedCaptchaPayloads.put(replayKey, expiresAt);
        }
    }

    private String nonceKey(String nonce) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(nonce.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void validateTurnstile(
            String captchaToken,
            String remoteIpAddress,
            AuthSecuritySettingsService.EffectiveAuthSecuritySettings settings) {
        JsonNode result = verifyRemoteCaptcha(
                URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify"),
                settings.registrationTurnstileSecret(),
                captchaToken,
                remoteIpAddress);
        if (!result.path("success").asBoolean(false)) {
            throw new IllegalArgumentException("Cloudflare Turnstile validation failed");
        }
    }

    private void validateHcaptcha(
            String captchaToken,
            String remoteIpAddress,
            AuthSecuritySettingsService.EffectiveAuthSecuritySettings settings) {
        JsonNode result = verifyRemoteCaptcha(
                URI.create("https://api.hcaptcha.com/siteverify"),
                settings.registrationHcaptchaSecret(),
                captchaToken,
                remoteIpAddress);
        if (!result.path("success").asBoolean(false)) {
            throw new IllegalArgumentException("hCaptcha validation failed");
        }
    }

    private JsonNode verifyRemoteCaptcha(URI uri, String secret, String captchaToken, String remoteIpAddress) {
        try {
            StringBuilder body = new StringBuilder();
            body.append("secret=").append(urlEncode(secret));
            body.append("&response=").append(urlEncode(captchaToken));
            if (remoteIpAddress != null && !remoteIpAddress.isBlank()) {
                body.append("&remoteip=").append(urlEncode(remoteIpAddress));
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(DEFAULT_REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Registration CAPTCHA provider could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Registration CAPTCHA provider request was interrupted", e);
        }
    }

    private void purgeUsedAltchaPayloads() {
        long now = System.currentTimeMillis();
        usedCaptchaPayloads.entrySet().removeIf((entry) -> entry.getValue() < now);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String effectiveAltchaHmacKey() {
        String configured = inboxBridgeConfig.security().auth().registrationCaptcha().altcha().hmacKey().orElse("");
        return configured != null && !configured.isBlank() ? configured.trim() : altchaHmacKey;
    }

    private RegistrationCaptchaProvider effectiveProvider(AuthSecuritySettingsService.EffectiveAuthSecuritySettings settings) {
        return RegistrationCaptchaProvider.valueOf(settings.registrationChallengeProvider().trim().toUpperCase(Locale.ROOT));
    }

    private void validateProviderConfigured(
            RegistrationCaptchaProvider provider,
            AuthSecuritySettingsService.EffectiveAuthSecuritySettings settings) {
        switch (provider) {
            case ALTCHA -> {
                return;
            }
            case TURNSTILE -> {
                if (!configured(settings.registrationTurnstileSiteKey()) || !configured(settings.registrationTurnstileSecret())) {
                    throw new IllegalStateException("Cloudflare Turnstile is not configured for self-registration");
                }
            }
            case HCAPTCHA -> {
                if (!configured(settings.registrationHcaptchaSiteKey()) || !configured(settings.registrationHcaptchaSecret())) {
                    throw new IllegalStateException("hCaptcha is not configured for self-registration");
                }
            }
        }
    }

    private boolean configured(String value) {
        return value != null && !value.isBlank() && !"replace-me".equalsIgnoreCase(value.trim());
    }

    public enum RegistrationCaptchaProvider {
        ALTCHA,
        TURNSTILE,
        HCAPTCHA
    }
}
