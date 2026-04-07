package dev.inboxbridge.service.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.data.exception.Base64UrlException;

import dev.inboxbridge.config.InboxBridgeConfig;
import dev.inboxbridge.dto.FinishPasskeyCeremonyRequest;
import dev.inboxbridge.dto.PasskeyView;
import dev.inboxbridge.dto.StartPasskeyCeremonyResponse;
import dev.inboxbridge.dto.StartPasskeyRegistrationRequest;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.PasskeyCeremony;
import dev.inboxbridge.persistence.PasskeyCeremonyRepository;
import dev.inboxbridge.persistence.UserPasskey;
import dev.inboxbridge.persistence.UserPasskeyRepository;
import dev.inboxbridge.service.admin.AppUserService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Owns WebAuthn passkey ceremonies for InboxBridge users.
 *
 * <p>Registration and authentication requests are verified server-side using a
 * standard WebAuthn relying-party implementation. Ceremony requests are stored
 * briefly in PostgreSQL so the browser flow remains stable across restarts and
 * reverse proxies.</p>
 */
@ApplicationScoped
public class PasskeyService {

    @Inject
    AppUserRepository appUserRepository;

    @Inject
    AppUserService appUserService;

    @Inject
    UserPasskeyRepository userPasskeyRepository;

    @Inject
    PasskeyCeremonyRepository passkeyCeremonyRepository;

    @Inject
    InboxBridgeConfig inboxBridgeConfig;

    public boolean isEnabled() {
        return inboxBridgeConfig.security().passkeys().enabled();
    }

    public List<PasskeyView> listForUser(Long userId) {
        return userPasskeyRepository.listByUserId(userId).stream()
                .map(this::toView)
                .toList();
    }

    public long countForUser(Long userId) {
        return userPasskeyRepository.countByUserId(userId);
    }

    @Transactional
    public StartPasskeyCeremonyResponse startRegistration(AppUser user, StartPasskeyRegistrationRequest request) {
        requireEnabled();
        cleanupExpiredCeremonies();
        try {
            AppUser managedUser = appUserService.ensureUserHandle(user.id);
            String label = normalizeLabel(request == null ? null : request.label(), managedUser.username);

            PublicKeyCredentialCreationOptions options = relyingParty().startRegistration(
                    StartRegistrationOptions.builder()
                            .user(UserIdentity.builder()
                                    .name(managedUser.username)
                                    .displayName(managedUser.username)
                                    .id(userHandle(managedUser))
                                    .build())
                            .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                                    .residentKey(ResidentKeyRequirement.REQUIRED)
                                    .userVerification(UserVerificationRequirement.REQUIRED)
                                    .build())
                            .build());

            PasskeyCeremony ceremony = new PasskeyCeremony();
            ceremony.id = ceremonyId();
            ceremony.userId = managedUser.id;
            ceremony.ceremonyType = PasskeyCeremony.CeremonyType.REGISTRATION;
            ceremony.requestJson = options.toJson();
            ceremony.label = label;
            ceremony.createdAt = Instant.now();
            ceremony.expiresAt = ceremony.createdAt.plus(challengeTtl());
            passkeyCeremonyRepository.persist(ceremony);

            return new StartPasskeyCeremonyResponse(ceremony.id, options.toCredentialsCreateJson());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start passkey registration: " + rootMessage(e), e);
        }
    }

    @Transactional
    public PasskeyView finishRegistration(AppUser user, FinishPasskeyCeremonyRequest request) {
        requireEnabled();
        cleanupExpiredCeremonies();
        PasskeyCeremony ceremony = requireCeremony(request.ceremonyId(), PasskeyCeremony.CeremonyType.REGISTRATION);
        if (!user.id.equals(ceremony.userId)) {
            throw new IllegalArgumentException("This passkey registration belongs to a different user.");
        }

        try {
            PublicKeyCredentialCreationOptions options = PublicKeyCredentialCreationOptions.fromJson(ceremony.requestJson);
            PublicKeyCredential<com.yubico.webauthn.data.AuthenticatorAttestationResponse, com.yubico.webauthn.data.ClientRegistrationExtensionOutputs> credential = PublicKeyCredential
                    .parseRegistrationResponseJson(request.credentialJson());
            RegistrationResult result = relyingParty().finishRegistration(FinishRegistrationOptions.builder()
                    .request(options)
                    .response(credential)
                    .build());

            UserPasskey passkey = new UserPasskey();
            passkey.userId = user.id;
            passkey.label = ceremony.label;
            passkey.credentialId = result.getKeyId().getId().getBase64Url();
            passkey.publicKeyCose = result.getPublicKeyCose().getBase64Url();
            passkey.signatureCount = result.getSignatureCount();
            passkey.aaguid = result.getAaguid().getBase64Url();
            passkey.discoverable = result.isDiscoverable().orElse(Boolean.TRUE);
            passkey.backupEligible = result.isBackupEligible();
            passkey.backedUp = result.isBackedUp();
            passkey.transports = result.getKeyId().getTransports()
                    .map(transports -> transports.stream().map(String::valueOf).collect(Collectors.joining(",")))
                    .orElse(null);
            passkey.createdAt = Instant.now();
            userPasskeyRepository.persist(passkey);
            ceremony.delete();
            return toView(passkey);
        } catch (Exception e) {
            throw new IllegalArgumentException("Passkey registration failed: " + rootMessage(e), e);
        }
    }

    @Transactional
    public StartPasskeyCeremonyResponse startAuthentication() {
        requireEnabled();
        cleanupExpiredCeremonies();
        try {
            AssertionRequest request = relyingParty().startAssertion(StartAssertionOptions.builder()
                    .userVerification(UserVerificationRequirement.REQUIRED)
                    .build());
            PasskeyCeremony ceremony = new PasskeyCeremony();
            ceremony.id = ceremonyId();
            ceremony.ceremonyType = PasskeyCeremony.CeremonyType.AUTHENTICATION;
            ceremony.requestJson = request.toJson();
            ceremony.passwordVerified = false;
            ceremony.createdAt = Instant.now();
            ceremony.expiresAt = ceremony.createdAt.plus(challengeTtl());
            passkeyCeremonyRepository.persist(ceremony);
            return new StartPasskeyCeremonyResponse(ceremony.id, request.toCredentialsGetJson());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start passkey sign-in: " + rootMessage(e), e);
        }
    }

    @Transactional
    public StartPasskeyCeremonyResponse startAuthenticationForUser(AppUser user, boolean passwordVerified) {
        requireEnabled();
        cleanupExpiredCeremonies();
        if (user == null || user.id == null) {
            throw new IllegalArgumentException("Unknown user");
        }
        try {
            AssertionRequest request = relyingParty().startAssertion(StartAssertionOptions.builder()
                    .username(user.username)
                    .userVerification(UserVerificationRequirement.REQUIRED)
                    .build());
            PasskeyCeremony ceremony = new PasskeyCeremony();
            ceremony.id = ceremonyId();
            ceremony.userId = user.id;
            ceremony.ceremonyType = PasskeyCeremony.CeremonyType.AUTHENTICATION;
            ceremony.requestJson = request.toJson();
            ceremony.passwordVerified = passwordVerified;
            ceremony.createdAt = Instant.now();
            ceremony.expiresAt = ceremony.createdAt.plus(challengeTtl());
            passkeyCeremonyRepository.persist(ceremony);
            return new StartPasskeyCeremonyResponse(ceremony.id, request.toCredentialsGetJson());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to start passkey sign-in: " + rootMessage(e), e);
        }
    }

    @Transactional
    public PasskeyAuthenticationResult finishAuthentication(FinishPasskeyCeremonyRequest request) {
        requireEnabled();
        cleanupExpiredCeremonies();
        PasskeyCeremony ceremony = requireCeremony(request.ceremonyId(), PasskeyCeremony.CeremonyType.AUTHENTICATION);

        try {
            AssertionRequest assertionRequest = AssertionRequest.fromJson(ceremony.requestJson);
            PublicKeyCredential<com.yubico.webauthn.data.AuthenticatorAssertionResponse, com.yubico.webauthn.data.ClientAssertionExtensionOutputs> credential = PublicKeyCredential
                    .parseAssertionResponseJson(request.credentialJson());
            com.yubico.webauthn.AssertionResult result = relyingParty().finishAssertion(FinishAssertionOptions.builder()
                    .request(assertionRequest)
                    .response(credential)
                    .build());

            AppUser user = appUserRepository.findByUsername(result.getUsername())
                    .filter(found -> found.active && found.approved)
                    .orElseThrow(() -> new IllegalArgumentException("Passkey sign-in failed"));
            if (ceremony.userId != null && !ceremony.userId.equals(user.id)) {
                throw new IllegalArgumentException("Passkey sign-in failed");
            }
            if (appUserService.hasPassword(user) && appUserService.requiresPasskey(user) && !ceremony.passwordVerified) {
                throw new IllegalArgumentException("Password validation is required before passkey sign-in for this account.");
            }
            UserPasskey storedPasskey = userPasskeyRepository.findByCredentialId(result.getCredential().getCredentialId().getBase64Url())
                    .orElseThrow(() -> new IllegalArgumentException("Passkey sign-in failed"));
            storedPasskey.signatureCount = result.getSignatureCount();
            storedPasskey.backedUp = result.isBackedUp();
            storedPasskey.lastUsedAt = Instant.now();
            ceremony.delete();
            return new PasskeyAuthenticationResult(user, ceremony.passwordVerified);
        } catch (Exception e) {
            throw new IllegalArgumentException("Passkey sign-in failed: " + rootMessage(e), e);
        }
    }

    public record PasskeyAuthenticationResult(AppUser user, boolean passwordVerified) {
    }

    @Transactional
    public void deleteForUser(AppUser user, Long passkeyId) {
        UserPasskey passkey = userPasskeyRepository.findByIdOptional(passkeyId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown passkey id"));
        if (!user.id.equals(passkey.userId)) {
            throw new IllegalArgumentException("That passkey belongs to a different user.");
        }
        AppUser managedUser = appUserService.findById(user.id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        if (!appUserService.hasPassword(managedUser) && userPasskeyRepository.countByUserId(user.id) <= 1) {
            throw new IllegalArgumentException("You cannot remove the only passkey from a passwordless account.");
        }
        passkey.delete();
    }

    @Transactional
    public long resetForUser(Long userId) {
        cleanupExpiredCeremonies();
        appUserRepository.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        return userPasskeyRepository.deleteByUserId(userId);
    }

    private PasskeyView toView(UserPasskey passkey) {
        return new PasskeyView(
                passkey.id,
                passkey.label,
                passkey.discoverable,
                passkey.backupEligible,
                passkey.backedUp,
                passkey.createdAt,
                passkey.lastUsedAt);
    }

    private PasskeyCeremony requireCeremony(String ceremonyId, PasskeyCeremony.CeremonyType ceremonyType) {
        return passkeyCeremonyRepository.findValid(ceremonyId, ceremonyType, Instant.now())
                .orElseThrow(() -> new IllegalArgumentException("Passkey ceremony expired. Start again from the admin UI."));
    }

    private void cleanupExpiredCeremonies() {
        passkeyCeremonyRepository.deleteExpired(Instant.now());
    }

    private RelyingParty relyingParty() {
        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder()
                        .id(inboxBridgeConfig.security().passkeys().rpId().trim())
                        .name(inboxBridgeConfig.security().passkeys().rpName().trim())
                        .build())
                .credentialRepository(new InboxBridgeCredentialRepository())
                .origins(allowedOrigins())
                .build();
    }

    private Set<String> allowedOrigins() {
        return Arrays.stream(inboxBridgeConfig.security().passkeys().origins().split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ByteArray userHandle(AppUser user) {
        return new ByteArray(user.userHandle.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeLabel(String rawLabel, String username) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return "Passkey for " + username;
        }
        return rawLabel.trim();
    }

    private String ceremonyId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Passkeys are disabled for this InboxBridge deployment.");
        }
    }

    private Duration challengeTtl() {
        return Duration.parse(inboxBridgeConfig.security().passkeys().challengeTtl());
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private final class InboxBridgeCredentialRepository implements CredentialRepository {

        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            return appUserRepository.findByUsername(username)
                    .map(user -> userPasskeyRepository.listByUserId(user.id).stream()
                            .map(passkey -> PublicKeyCredentialDescriptor.builder()
                                    .id(byteArray(passkey.credentialId))
                                    .build())
                            .collect(Collectors.toCollection(LinkedHashSet::new)))
                    .orElseGet(LinkedHashSet::new);
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            return appUserRepository.findByUsername(username).map(PasskeyService.this::userHandle);
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            String handle = new String(userHandle.getBytes(), StandardCharsets.UTF_8);
            return appUserRepository.findByUserHandle(handle).map(user -> user.username);
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            String handle = new String(userHandle.getBytes(), StandardCharsets.UTF_8);
            return appUserRepository.findByUserHandle(handle)
                    .flatMap(user -> userPasskeyRepository.findByCredentialId(credentialId.getBase64Url())
                            .filter(passkey -> passkey.userId.equals(user.id)))
                    .map(this::toRegisteredCredential);
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            return userPasskeyRepository.findByCredentialId(credentialId.getBase64Url())
                    .map(passkey -> Set.of(toRegisteredCredential(passkey)))
                    .orElse(Set.of());
        }

        private RegisteredCredential toRegisteredCredential(UserPasskey passkey) {
            AppUser user = appUserRepository.findByIdOptional(passkey.userId).orElseThrow();
            RegisteredCredential.RegisteredCredentialBuilder builder = RegisteredCredential.builder()
                    .credentialId(byteArray(passkey.credentialId))
                    .userHandle(userHandle(user))
                    .publicKeyCose(byteArray(passkey.publicKeyCose))
                    .signatureCount(passkey.signatureCount);
            if (passkey.backupEligible) {
                builder.backupEligible(Boolean.TRUE);
            }
            if (passkey.backedUp) {
                builder.backupState(Boolean.TRUE);
            }
            return builder.build();
        }

        private ByteArray byteArray(String base64Url) {
            try {
                return ByteArray.fromBase64Url(base64Url);
            } catch (Base64UrlException e) {
                throw new IllegalStateException("Stored passkey data is corrupted", e);
            }
        }
    }
}
