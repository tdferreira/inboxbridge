package dev.inboxbridge.web.oauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class MicrosoftOAuthCallbackPageRendererTest {

    private final MicrosoftOAuthCallbackPageRenderer renderer = new MicrosoftOAuthCallbackPageRenderer();

    @Test
    void successPageRendersExpectedBrowserFlowControls() {
        String html = renderer.renderPage(
                "en",
                "Microsoft OAuth Code Received",
                "Secure token storage is enabled.",
                Map.of("Authorization Code", "code-123"),
                true,
                "outlook-main",
                "",
                "state-1",
                "code-123");

        assertTrue(html.contains("Microsoft OAuth Code Received"));
        assertTrue(html.contains("Exchange Code In Browser"));
        assertTrue(html.contains("Cancel automatic redirect"));
        assertTrue(html.contains("const callbackParams = new URLSearchParams(window.location.search);"));
        assertTrue(html.contains("function cancelAutoReturn()"));
        assertTrue(html.contains("outlook-main"));
        assertTrue(html.contains("code-123"));
    }

    @Test
    void errorPageEscapesUntrustedValues() {
        String html = renderer.renderPage(
                "en",
                "Microsoft OAuth Error",
                "Something failed",
                Map.of("Error", "<bad>", "Description", "\"quoted\""),
                false,
                null,
                null,
                null,
                null);

        assertTrue(html.contains("&lt;bad&gt;"));
        assertTrue(html.contains("&quot;quoted&quot;"));
        assertFalse(html.contains("<bad>"));
    }
}
