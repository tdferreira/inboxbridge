package dev.inboxbridge.service.auth;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SessionClientInfoService {

    public SessionClientInfo describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new SessionClientInfo("Unknown browser", "Unknown device");
        }
        String normalized = userAgent.toLowerCase();
        return new SessionClientInfo(resolveBrowserLabel(normalized), resolveDeviceLabel(normalized));
    }

    private String resolveBrowserLabel(String userAgent) {
        if (userAgent.contains("edg/")) {
            return "Microsoft Edge";
        }
        if (userAgent.contains("opr/") || userAgent.contains("opera")) {
            return "Opera";
        }
        if (userAgent.contains("samsungbrowser/")) {
            return "Samsung Internet";
        }
        if (userAgent.contains("fxios/")) {
            return "Firefox";
        }
        if (userAgent.contains("firefox/")) {
            return "Firefox";
        }
        if (userAgent.contains("crios/")) {
            return "Chrome";
        }
        if (userAgent.contains("chrome/") && !userAgent.contains("edg/") && !userAgent.contains("opr/")) {
            if (userAgent.contains("wv")) {
                return "Android WebView";
            }
            return "Chrome";
        }
        if ((userAgent.contains("safari/") || userAgent.contains("applewebkit/"))
                && !userAgent.contains("chrome/")
                && !userAgent.contains("crios/")
                && !userAgent.contains("android")) {
            return "Safari";
        }
        if (userAgent.contains("trident/") || userAgent.contains("msie")) {
            return "Internet Explorer";
        }
        return "Unknown browser";
    }

    private String resolveDeviceLabel(String userAgent) {
        String platform = resolvePlatformLabel(userAgent);
        if (userAgent.contains("ipad") || userAgent.contains("tablet") || (userAgent.contains("android") && !userAgent.contains("mobile"))) {
            return withPlatform("Tablet", platform);
        }
        if (userAgent.contains("iphone") || userAgent.contains("ipod") || userAgent.contains("mobile")) {
            return withPlatform("Mobile phone", platform);
        }
        if (userAgent.contains("smart-tv") || userAgent.contains("smarttv") || userAgent.contains("hbbtv") || userAgent.contains("googletv")) {
            return withPlatform("Smart TV", platform);
        }
        if (userAgent.contains("macintosh")
                || userAgent.contains("windows")
                || userAgent.contains("x11")
                || userAgent.contains("linux")
                || userAgent.contains("cros")) {
            return withPlatform("Desktop", platform);
        }
        return withPlatform("Unknown device", platform);
    }

    private String resolvePlatformLabel(String userAgent) {
        if (userAgent.contains("iphone") || userAgent.contains("ipad") || userAgent.contains("ipod")) {
            return "iOS";
        }
        if (userAgent.contains("android")) {
            return "Android";
        }
        if (userAgent.contains("windows")) {
            return "Windows";
        }
        if (userAgent.contains("mac os x") || userAgent.contains("macintosh")) {
            return "macOS";
        }
        if (userAgent.contains("cros")) {
            return "ChromeOS";
        }
        if (userAgent.contains("linux") || userAgent.contains("x11")) {
            return "Linux";
        }
        return "";
    }

    private String withPlatform(String baseLabel, String platform) {
        if (platform == null || platform.isBlank() || baseLabel.startsWith("Unknown")) {
            return baseLabel;
        }
        return baseLabel + " (" + platform + ")";
    }

    public record SessionClientInfo(String browserLabel, String deviceLabel) {
    }
}
