package dev.inboxbridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;

@QuarkusComponentTest
@TestConfigProperty(key = "PUBLIC_HOSTNAME", value = "mail.example.test")
@TestConfigProperty(key = "PUBLIC_PORT", value = "3443")
class PublicUrlServiceQuarkusTest {

    @Inject
    PublicUrlService publicUrlService;

    @Test
    void derivesBrowserFacingUrlsFromPublicHostnameAndPort() {
        assertEquals("https://mail.example.test:3443", publicUrlService.publicBaseUrl());
        assertEquals("https://mail.example.test:3443/api/google-oauth/callback", publicUrlService.googleRedirectUri());
        assertEquals("https://mail.example.test:3443/api/microsoft-oauth/callback", publicUrlService.microsoftRedirectUri());
    }
}
