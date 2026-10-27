package dev.inboxbridge.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.ExtensionSession;
import dev.inboxbridge.persistence.ExtensionSessionRepository;
import dev.inboxbridge.service.extension.ExtensionSessionService;
import dev.inboxbridge.testsupport.ScopedLogCapture;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.UriInfo;

class ExtensionAuthFilterTest {
    private static final String TOKEN = "synthetic-extension-token";
    private static final String FORGED_PATH = "api/extension/status\r\nFORGED token=" + TOKEN;

    @Test
    void missingOrUnsupportedCredentialsFailClosedBeforeAuthentication() {
        for (String header : new String[] {null, "", "Basic credentials", "bearer " + TOKEN, "Bearer"}) {
            Fixture fixture = fixture(header);
            assertRejectedWithoutRequestData(fixture);
            verifyNoInteractions(fixture.filter.extensionSessionService,
                    fixture.filter.extensionSessionRepository, fixture.filter.appUserRepository);
        }
    }

    @Test
    void invalidOrBlankBearerNeverLooksUpAUserOrSetsAContext() {
        for (String token : new String[] {"", TOKEN}) {
            Fixture fixture = fixture("Bearer " + token);
            when(fixture.filter.extensionSessionService.authenticate(token)).thenReturn(Optional.empty());
            assertRejectedWithoutRequestData(fixture);
            verify(fixture.filter.extensionSessionService).authenticate(token);
            verifyNoInteractions(fixture.filter.extensionSessionRepository, fixture.filter.appUserRepository);
        }
    }

    @Test
    void aDeletedSessionFailsClosedWithoutLookingUpAUser() {
        Fixture fixture = authenticatedFixture();
        when(fixture.filter.extensionSessionRepository.findByIdOptional(11L)).thenReturn(Optional.empty());
        assertRejectedWithoutRequestData(fixture);
        verifyNoInteractions(fixture.filter.appUserRepository);
    }

    @Test
    void missingInactiveAndUnapprovedUsersFailClosed() {
        for (AppUser user : new AppUser[] {null, user(false, true), user(true, false), user(false, false)}) {
            Fixture fixture = authenticatedFixture();
            when(fixture.filter.appUserRepository.findByIdOptional(4L)).thenReturn(Optional.ofNullable(user));
            assertRejectedWithoutRequestData(fixture);
        }
    }

    @Test
    void onlyAValidSessionAndActiveApprovedUserPopulateTheContext() {
        Fixture fixture = authenticatedFixture();
        fixture.filter.filter(fixture.request);
        verify(fixture.filter.extensionSessionService).authenticate(TOKEN);
        assertEquals(4L, fixture.filter.currentUserContext.user().id);
        assertEquals(11L, fixture.filter.currentUserContext.extensionSession().id);
    }

    private static void assertRejectedWithoutRequestData(Fixture fixture) {
        List<ScopedLogCapture.CapturedRecord> records;
        try (ScopedLogCapture capture = ScopedLogCapture.captureWarnings(ExtensionAuthFilter.class)) {
            assertThrows(NotAuthorizedException.class, () -> fixture.filter.filter(fixture.request));
            records = capture.records();
        }
        assertNull(fixture.filter.currentUserContext.user());
        assertNull(fixture.filter.currentUserContext.extensionSession());
        assertEquals(1, records.size());
        String message = records.getFirst().message();
        assertFalse(message.contains("\r"), message);
        assertFalse(message.contains("\n"), message);
        assertFalse(message.contains("FORGED"), message);
        assertFalse(message.contains(TOKEN), message);
    }

    private static Fixture authenticatedFixture() {
        Fixture fixture = fixture("Bearer \t" + TOKEN + " \t");
        when(fixture.filter.extensionSessionService.authenticate(TOKEN)).thenReturn(Optional.of(
                new ExtensionSessionService.AuthenticatedExtensionSession(11L, 4L, "test")));
        ExtensionSession session = new ExtensionSession();
        session.id = 11L;
        session.userId = 4L;
        when(fixture.filter.extensionSessionRepository.findByIdOptional(11L)).thenReturn(Optional.of(session));
        when(fixture.filter.appUserRepository.findByIdOptional(4L)).thenReturn(Optional.of(user(true, true)));
        return fixture;
    }

    private static AppUser user(boolean active, boolean approved) {
        AppUser user = new AppUser();
        user.id = 4L;
        user.active = active;
        user.approved = approved;
        return user;
    }

    private static Fixture fixture(String authorization) {
        ExtensionAuthFilter filter = new ExtensionAuthFilter();
        filter.extensionSessionService = mock(ExtensionSessionService.class);
        filter.extensionSessionRepository = mock(ExtensionSessionRepository.class);
        filter.appUserRepository = mock(AppUserRepository.class);
        filter.currentUserContext = new CurrentUserContext();
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        UriInfo uri = mock(UriInfo.class);
        when(request.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(authorization);
        when(request.getUriInfo()).thenReturn(uri);
        when(request.getMethod()).thenReturn("GET");
        when(uri.getPath()).thenReturn(FORGED_PATH);
        return new Fixture(filter, request);
    }

    private record Fixture(ExtensionAuthFilter filter, ContainerRequestContext request) { }
}
