package dev.inboxbridge.service.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SessionClientInfoServiceTest {

    private final SessionClientInfoService service = new SessionClientInfoService();

    @Test
    void identifiesIPhoneSafariSessions() {
        SessionClientInfoService.SessionClientInfo info = service.describe(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1");

        assertEquals("Safari", info.browserLabel());
        assertEquals("Mobile phone (iOS)", info.deviceLabel());
    }

    @Test
    void identifiesAndroidChromeSessions() {
        SessionClientInfoService.SessionClientInfo info = service.describe(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36");

        assertEquals("Chrome", info.browserLabel());
        assertEquals("Mobile phone (Android)", info.deviceLabel());
    }

    @Test
    void identifiesDesktopEdgeSessions() {
        SessionClientInfoService.SessionClientInfo info = service.describe(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 Edg/134.0.0.0");

        assertEquals("Microsoft Edge", info.browserLabel());
        assertEquals("Desktop (Windows)", info.deviceLabel());
    }

    @Test
    void fallsBackGracefullyWhenUserAgentIsMissing() {
        SessionClientInfoService.SessionClientInfo info = service.describe(null);

        assertEquals("Unknown browser", info.browserLabel());
        assertEquals("Unknown device", info.deviceLabel());
    }
}
