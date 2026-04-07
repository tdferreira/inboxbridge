package dev.inboxbridge.web.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GoogleOAuthCallbackPageRendererTest {

    private final GoogleOAuthCallbackPageRenderer renderer = new GoogleOAuthCallbackPageRenderer();

    @Test
    void callbackPageRendersExpectedBrowserFlowControls() {
        String html = renderer.renderCallbackPage(
                "en",
                "Secure token storage is enabled.",
                "code-123",
                "state-1",
                null,
                null);

        assertTrue(html.contains("Google OAuth Callback"));
        assertTrue(html.contains("Google OAuth Code Received"));
        assertTrue(html.contains("Exchange Code In Browser"));
        assertTrue(html.contains("Copy Code"));
        assertTrue(html.contains("Cancel automatic redirect"));
        assertTrue(html.contains("window.setTimeout(() => {"));
        assertTrue(html.contains("callbackParams.get('code')"));
        assertTrue(html.contains("code-123"));
        assertTrue(html.contains("state-1"));
        assertFalse(html.contains("<script>alert("));
    }

    @Test
    void errorPageEscapesUntrustedValues() {
        String html = renderer.renderErrorPage(
                "en",
                "Google OAuth Error",
                "Something failed",
                "<bad>",
                "\"quoted\"");

        assertTrue(html.contains("&lt;bad&gt;"));
        assertTrue(html.contains("&quot;quoted&quot;"));
        assertFalse(html.contains("<bad>"));
    }
}
