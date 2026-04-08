package dev.inboxbridge.service.auth;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.persistence.GeoIpCacheEntry;
import dev.inboxbridge.persistence.GeoIpCacheEntryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class GeoIpLocationService {

    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_PROVIDER_COOLDOWN = Duration.ofMinutes(5);

    @Inject
    GeoIpCacheEntryRepository repository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AuthSecuritySettingsService authSecuritySettingsService;

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(DEFAULT_REQUEST_TIMEOUT)
            .build();

    private final ConcurrentHashMap<GeoIpProvider, Instant> providerCooldowns = new ConcurrentHashMap<>();

    public boolean isConfigured() {
        EffectiveGeoIpSettings settings = effectiveSettings();
        if (!settings.enabled()) {
            return false;
        }
        return providerChain(settings).stream().anyMatch((provider) -> providerConfigured(provider, settings));
    }

    @Transactional
    public Optional<String> resolveLocation(String clientIp) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        String normalizedIp = normalizePublicIp(clientIp);
        if (normalizedIp == null) {
            return Optional.empty();
        }
        EffectiveGeoIpSettings settings = effectiveSettings();
        Instant now = Instant.now();
        repository.deleteExpired(now);
        Optional<GeoIpCacheEntry> cached = repository.findValid(normalizedIp, now);
        if (cached.isPresent()) {
            return Optional.ofNullable(cached.get().locationLabel);
        }

        for (GeoIpProvider provider : providerChain(settings)) {
            if (!providerConfigured(provider, settings) || providerCoolingDown(provider, now)) {
                continue;
            }
            LookupResult result = lookup(provider, normalizedIp, settings);
            if (result.status == LookupStatus.RETRYABLE_FAILURE) {
                providerCooldowns.put(provider, now.plus(providerCooldown(settings)));
                continue;
            }
            cacheResult(normalizedIp, provider, result, now, settings);
            return Optional.ofNullable(result.locationLabel);
        }
        return Optional.empty();
    }

    private LookupResult lookup(GeoIpProvider provider, String ipAddress, EffectiveGeoIpSettings settings) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(provider.lookupUri(ipAddress, settings.ipinfoToken()))
                            .header("Accept", "application/json")
                            .timeout(requestTimeout(settings))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(provider, response);
        } catch (IOException e) {
            return LookupResult.retryableFailure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LookupResult.retryableFailure();
        }
    }

    private LookupResult parseResponse(GeoIpProvider provider, HttpResponse<String> response) {
        int statusCode = response.statusCode();
        if (statusCode == 429 || statusCode >= 500) {
            return LookupResult.retryableFailure();
        }
        if (statusCode >= 400) {
            return LookupResult.notFound();
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            return switch (provider) {
                case IPWHOIS -> parseIpwhois(root);
                case IPAPI_CO -> parseIpapiCo(root);
                case IP_API -> parseIpApi(root);
                case IPINFO_LITE -> parseIpinfoLite(root);
                case NONE -> LookupResult.notFound();
            };
        } catch (Exception e) {
            return LookupResult.retryableFailure();
        }
    }

    private LookupResult parseIpwhois(JsonNode root) {
        if (root.path("success").isBoolean() && !root.path("success").asBoolean()) {
            return LookupResult.notFound();
        }
        if (root.path("bogon").asBoolean(false)) {
            return LookupResult.notFound();
        }
        String location = joinLocationParts(
                textValue(root, "city"),
                textValue(root, "region"),
                textValue(root, "country"));
        return LookupResult.found(location);
    }

    private LookupResult parseIpinfoLite(JsonNode root) {
        if (root.path("bogon").asBoolean(false)) {
            return LookupResult.notFound();
        }
        String location = joinLocationParts(
                null,
                null,
                textValue(root, "country"));
        return LookupResult.found(location);
    }

    private LookupResult parseIpapiCo(JsonNode root) {
        if (root.path("error").asBoolean(false)) {
            return LookupResult.notFound();
        }
        String location = joinLocationParts(
                textValue(root, "city"),
                textValue(root, "region"),
                textValue(root, "country_name"));
        return LookupResult.found(location);
    }

    private LookupResult parseIpApi(JsonNode root) {
        if ("fail".equalsIgnoreCase(textValue(root, "status"))) {
            String message = textValue(root, "message");
            if ("private range".equalsIgnoreCase(message) || "reserved range".equalsIgnoreCase(message)) {
                return LookupResult.notFound();
            }
            return LookupResult.retryableFailure();
        }
        String location = joinLocationParts(
                textValue(root, "city"),
                textValue(root, "regionName"),
                textValue(root, "country"));
        return LookupResult.found(location);
    }

    private void cacheResult(String ipAddress, GeoIpProvider provider, LookupResult result, Instant now, EffectiveGeoIpSettings settings) {
        GeoIpCacheEntry entry = repository.findByIpAddress(ipAddress).orElseGet(GeoIpCacheEntry::new);
        entry.ipAddress = ipAddress;
        entry.provider = provider.name();
        entry.locationLabel = result.locationLabel;
        entry.resolutionStatus = result.status.name();
        entry.updatedAt = now;
        entry.expiresAt = now.plus(cacheTtl(settings));
        repository.persist(entry);
    }

    boolean providerCoolingDown(GeoIpProvider provider, Instant now) {
        Instant cooldownUntil = providerCooldowns.get(provider);
        if (cooldownUntil == null) {
            return false;
        }
        if (now.isBefore(cooldownUntil)) {
            return true;
        }
        providerCooldowns.remove(provider, cooldownUntil);
        return false;
    }

    private List<GeoIpProvider> providerChain(EffectiveGeoIpSettings settings) {
        Set<GeoIpProvider> providers = new LinkedHashSet<>();
        parseProvider(settings.primaryProvider()).ifPresent(providers::add);
        if (settings.fallbackProviders() != null) {
            for (String rawProvider : settings.fallbackProviders().split(",")) {
                parseProvider(rawProvider).ifPresent(providers::add);
            }
        }
        return new ArrayList<>(providers);
    }

    private Optional<GeoIpProvider> parseProvider(String rawProvider) {
        if (rawProvider == null || rawProvider.isBlank()) {
            return Optional.empty();
        }
        try {
            GeoIpProvider provider = GeoIpProvider.valueOf(rawProvider.trim().toUpperCase(Locale.ROOT));
            return provider == GeoIpProvider.NONE ? Optional.empty() : Optional.of(provider);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private boolean providerConfigured(GeoIpProvider provider, EffectiveGeoIpSettings settings) {
        return switch (provider) {
            case IPWHOIS -> true;
            case IPAPI_CO -> true;
            case IP_API -> true;
            case IPINFO_LITE -> settings.ipinfoToken() != null
                    && !settings.ipinfoToken().trim().isBlank()
                    && !"replace-me".equalsIgnoreCase(settings.ipinfoToken().trim());
            case NONE -> false;
        };
    }

    private String normalizePublicIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank() || "unknown".equalsIgnoreCase(clientIp.trim())) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(clientIp.trim());
            if (address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                return null;
            }
            if (address instanceof Inet4Address inet4 && isReservedIpv4(inet4)) {
                return null;
            }
            if (address instanceof Inet6Address inet6 && (inet6.isIPv4CompatibleAddress() || inet6.isMCGlobal())) {
                return inet6.getHostAddress();
            }
            return address.getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isReservedIpv4(Inet4Address address) {
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 0)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || first >= 224;
    }

    private String joinLocationParts(String city, String region, String country) {
        List<String> parts = new ArrayList<>();
        appendLocationPart(parts, city);
        appendLocationPart(parts, region);
        appendLocationPart(parts, country);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private void appendLocationPart(List<String> parts, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (parts.stream().noneMatch(existing -> existing.equalsIgnoreCase(value.trim()))) {
            parts.add(value.trim());
        }
    }

    private String textValue(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isValueNode()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Duration requestTimeout(EffectiveGeoIpSettings settings) {
        return settings.requestTimeout() == null || settings.requestTimeout().isZero() || settings.requestTimeout().isNegative()
                ? DEFAULT_REQUEST_TIMEOUT
                : settings.requestTimeout();
    }

    private Duration providerCooldown(EffectiveGeoIpSettings settings) {
        return settings.providerCooldown() == null || settings.providerCooldown().isZero() || settings.providerCooldown().isNegative()
                ? DEFAULT_PROVIDER_COOLDOWN
                : settings.providerCooldown();
    }

    private Duration cacheTtl(EffectiveGeoIpSettings settings) {
        return settings.cacheTtl() == null || settings.cacheTtl().isZero() || settings.cacheTtl().isNegative()
                ? Duration.ofDays(30)
                : settings.cacheTtl();
    }

    private EffectiveGeoIpSettings effectiveSettings() {
        AuthSecuritySettingsService.EffectiveAuthSecuritySettings effective = authSecuritySettingsService.effectiveSettings();
        return new EffectiveGeoIpSettings(
                effective.geoIpEnabled(),
                effective.geoIpPrimaryProvider(),
                effective.geoIpFallbackProviders(),
                effective.geoIpCacheTtl(),
                effective.geoIpProviderCooldown(),
                effective.geoIpRequestTimeout(),
                effective.geoIpIpinfoToken());
    }

    public enum GeoIpProvider {
        NONE,
        IPWHOIS,
        IPAPI_CO,
        IP_API,
        IPINFO_LITE;

        URI lookupUri(String ipAddress, String ipinfoToken) {
            return switch (this) {
                case IPWHOIS -> URI.create("https://ipwho.is/" + URLEncoder.encode(ipAddress, StandardCharsets.UTF_8));
                case IPAPI_CO -> URI.create("https://ipapi.co/" + URLEncoder.encode(ipAddress, StandardCharsets.UTF_8) + "/json/");
                case IP_API -> URI.create("http://ip-api.com/json/" + URLEncoder.encode(ipAddress, StandardCharsets.UTF_8));
                case IPINFO_LITE -> URI.create("https://api.ipinfo.io/lite/"
                        + URLEncoder.encode(ipAddress, StandardCharsets.UTF_8)
                        + "?token=" + URLEncoder.encode(ipinfoToken == null ? "" : ipinfoToken.trim(), StandardCharsets.UTF_8));
                case NONE -> throw new IllegalStateException("Geo-IP provider NONE cannot resolve locations");
            };
        }
    }

    enum LookupStatus {
        FOUND,
        NOT_FOUND,
        RETRYABLE_FAILURE
    }

    static final class LookupResult {
        private final LookupStatus status;
        private final String locationLabel;

        private LookupResult(LookupStatus status, String locationLabel) {
            this.status = status;
            this.locationLabel = locationLabel;
        }

        static LookupResult found(String locationLabel) {
            return new LookupResult(locationLabel == null ? LookupStatus.NOT_FOUND : LookupStatus.FOUND, locationLabel);
        }

        static LookupResult notFound() {
            return new LookupResult(LookupStatus.NOT_FOUND, null);
        }

        static LookupResult retryableFailure() {
            return new LookupResult(LookupStatus.RETRYABLE_FAILURE, null);
        }
    }

    record EffectiveGeoIpSettings(
            boolean enabled,
            String primaryProvider,
            String fallbackProviders,
            Duration cacheTtl,
            Duration providerCooldown,
            Duration requestTimeout,
            String ipinfoToken) {
    }
}
