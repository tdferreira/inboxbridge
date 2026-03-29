package dev.inboxbridge.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.inboxbridge.dto.AdminResetPasswordRequest;
import dev.inboxbridge.dto.CreateUserRequest;
import dev.inboxbridge.dto.RegisterUserRequest;
import dev.inboxbridge.dto.UpdateUserRequest;
import dev.inboxbridge.dto.UserSummaryResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.ImportedMessageRepository;
import dev.inboxbridge.persistence.SourcePollEventRepository;
import dev.inboxbridge.persistence.SourcePollingSettingRepository;
import dev.inboxbridge.persistence.SourcePollingStateRepository;
import dev.inboxbridge.persistence.UserEmailAccountRepository;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
import dev.inboxbridge.persistence.UserMailDestinationConfigRepository;
import dev.inboxbridge.persistence.UserPasskeyRepository;
import dev.inboxbridge.persistence.UserPollingSettingRepository;
import dev.inboxbridge.persistence.UserUiPreferenceRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
/**
 * Owns InboxBridge application-user lifecycle rules, including bootstrap admin
 * creation, self-registration, approval state, password changes, and the
 * invariant that at least one approved active admin must always remain.
 */
public class AppUserService {

    @Inject
    AppUserRepository repository;

    @Inject
    PasswordHashService passwordHashService;

    @Inject
    UserGmailConfigRepository userGmailConfigRepository;

    @Inject
    UserMailDestinationConfigRepository userMailDestinationConfigRepository;

    @Inject
    UserEmailAccountRepository userEmailAccountRepository;

    @Inject
    UserPasskeyRepository userPasskeyRepository;

    @Inject
    UserPollingSettingRepository userPollingSettingRepository;

    @Inject
    UserUiPreferenceRepository userUiPreferenceRepository;

    @Inject
    OAuthCredentialService oAuthCredentialService;

    @Inject
    UserSessionService userSessionService;

    @Inject
    SourcePollingSettingRepository sourcePollingSettingRepository;

    @Inject
    SourcePollingStateRepository sourcePollingStateRepository;

    @Inject
    SourcePollEventRepository sourcePollEventRepository;

    @Inject
    ImportedMessageRepository importedMessageRepository;

    @Inject
    UserMailDestinationConfigService userMailDestinationConfigService;

    void onStartup(@Observes StartupEvent event) {
        ensureBootstrapAdmin();
        ensureUserHandles();
    }

    public Optional<AppUser> findByUsername(String username) {
        return repository.findByUsername(username);
    }

    public Optional<AppUser> findById(Long id) {
        return repository.findByIdOptional(id);
    }

    public List<UserSummaryResponse> listUsers() {
        return repository.listAll().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public AppUser createUser(CreateUserRequest request) {
        String username = validateNewUsername(request.username());
        AppUser user = new AppUser();
        user.username = username;
        user.userHandle = generateUserHandle();
        user.passwordHash = passwordHashService.hash(validatePasswordStrength(request.password(), null));
        user.role = parseRole(request.role());
        user.mustChangePassword = true;
        user.active = true;
        user.approved = true;
        user.disabledBySingleUserMode = false;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        repository.persist(user);
        return user;
    }

    @Transactional
    public AppUser registerUser(RegisterUserRequest request) {
        String username = validateNewUsername(request.username());
        requirePasswordConfirmation(request.password(), request.confirmPassword());
        AppUser user = new AppUser();
        user.username = username;
        user.userHandle = generateUserHandle();
        user.passwordHash = passwordHashService.hash(validatePasswordStrength(request.password(), null));
        user.role = AppUser.Role.USER;
        user.mustChangePassword = false;
        user.active = false;
        user.approved = false;
        user.disabledBySingleUserMode = false;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        repository.persist(user);
        return user;
    }

    @Transactional
    public void changePassword(AppUser user, String currentPassword, String newPassword, String confirmNewPassword) {
        AppUser managedUser = repository.findByIdOptional(user.id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        if (hasPassword(managedUser) && !passwordMatches(managedUser, currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        requirePasswordConfirmation(newPassword, confirmNewPassword);
        String validatedPassword = validatePasswordStrength(newPassword, currentPassword);
        managedUser.passwordHash = passwordHashService.hash(validatedPassword);
        managedUser.mustChangePassword = false;
        managedUser.updatedAt = Instant.now();
    }

    public boolean passwordMatches(AppUser user, String rawPassword) {
        return hasPassword(user) && rawPassword != null && passwordHashService.matches(rawPassword, user.passwordHash);
    }

    public boolean hasPassword(AppUser user) {
        return user != null && user.passwordHash != null && !user.passwordHash.isBlank();
    }

    public boolean requiresPasskey(AppUser user) {
        return user != null && user.id != null && userPasskeyRepository.countByUserId(user.id) > 0;
    }

    public long passkeyCount(Long userId) {
        return userPasskeyRepository.countByUserId(userId);
    }

    @Transactional
    public void removePassword(AppUser user, String currentPassword) {
        AppUser managedUser = repository.findByIdOptional(user.id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        if (!hasPassword(managedUser)) {
            throw new IllegalArgumentException("This account does not have a password configured.");
        }
        if (!passwordMatches(managedUser, currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passkeyCount(managedUser.id) == 0) {
            throw new IllegalArgumentException("Register a passkey before removing the password.");
        }
        managedUser.passwordHash = null;
        managedUser.mustChangePassword = false;
        managedUser.updatedAt = Instant.now();
    }

    @Transactional
    public void ensureBootstrapAdmin() {
        if (repository.count() > 0) {
            return;
        }
        AppUser admin = new AppUser();
        admin.username = "admin";
        admin.userHandle = generateUserHandle();
        admin.passwordHash = passwordHashService.hash("nimda");
        admin.role = AppUser.Role.ADMIN;
        admin.mustChangePassword = true;
        admin.active = true;
        admin.approved = true;
        admin.disabledBySingleUserMode = false;
        admin.createdAt = Instant.now();
        admin.updatedAt = admin.createdAt;
        repository.persist(admin);
    }

    @Transactional
    public AppUser updateUser(AppUser actor, Long userId, UpdateUserRequest request) {
        AppUser user = repository.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        AppUser.Role newRole = request.role() == null || request.role().isBlank() ? user.role : parseRole(request.role());
        boolean newActive = request.active() == null ? user.active : request.active();
        boolean newApproved = request.approved() == null ? user.approved : request.approved();

        if (actor != null
                && actor.id != null
                && actor.id.equals(user.id)
                && user.role == AppUser.Role.ADMIN
                && newRole != AppUser.Role.ADMIN) {
            throw new IllegalArgumentException("Admins cannot remove their own admin rights.");
        }

        if (user.role == AppUser.Role.ADMIN && repository.countApprovedAdmins() == 1) {
            if (newRole != AppUser.Role.ADMIN || !newActive || !newApproved) {
                throw new IllegalArgumentException("At least one approved active admin must remain.");
            }
        }

        user.role = newRole;
        user.active = newActive;
        user.approved = newApproved;
        if (request.mustChangePassword() != null) {
            user.mustChangePassword = request.mustChangePassword();
        }
        user.updatedAt = Instant.now();
        return user;
    }

    @Transactional
    public void switchToSingleUserMode(AppUser actor) {
        AppUser actingAdmin = repository.findByIdOptional(actor.id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        if (actingAdmin.role != AppUser.Role.ADMIN) {
            throw new IllegalArgumentException("Admin access is required");
        }

        for (AppUser user : repository.listAll()) {
            if (user.id.equals(actingAdmin.id)) {
                user.active = true;
                user.approved = true;
                user.disabledBySingleUserMode = false;
                user.updatedAt = Instant.now();
                continue;
            }
            boolean wasActive = user.active;
            user.active = false;
            user.disabledBySingleUserMode = wasActive;
            user.updatedAt = Instant.now();
            userSessionService.invalidateUserSessions(user.id);
        }
    }

    @Transactional
    public void switchToMultiUserMode() {
        for (AppUser user : repository.listDisabledBySingleUserMode()) {
            user.active = true;
            user.disabledBySingleUserMode = false;
            user.updatedAt = Instant.now();
        }
    }

    @Transactional
    public void deleteUser(AppUser actor, Long userId) {
        if (actor != null && actor.id != null && actor.id.equals(userId)) {
            throw new IllegalArgumentException("Admins cannot delete their own account.");
        }
        AppUser user = repository.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));

        if (user.role == AppUser.Role.ADMIN && user.active && user.approved && repository.countApprovedAdmins() == 1) {
            throw new IllegalArgumentException("At least one approved active admin must remain.");
        }

        deleteOwnedData(user);
        repository.delete(user);
    }

    @Transactional
    public AppUser adminResetPassword(AppUser actor, Long userId, AdminResetPasswordRequest request) {
        if (actor == null || actor.role != AppUser.Role.ADMIN) {
            throw new IllegalArgumentException("Admin access is required");
        }
        AppUser user = repository.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        requirePasswordConfirmation(request.newPassword(), request.confirmNewPassword());
        user.passwordHash = passwordHashService.hash(validatePasswordStrength(request.newPassword(), null));
        user.mustChangePassword = true;
        user.updatedAt = Instant.now();
        return user;
    }

    @Transactional
    public AppUser ensureUserHandle(Long userId) {
        AppUser user = repository.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        if (user.userHandle == null || user.userHandle.isBlank()) {
            user.userHandle = generateUserHandle();
            user.updatedAt = Instant.now();
        }
        return user;
    }

    @Transactional
    public void ensureUserHandles() {
        for (AppUser user : repository.listAll()) {
            if (user.userHandle == null || user.userHandle.isBlank()) {
                user.userHandle = generateUserHandle();
                user.updatedAt = Instant.now();
            }
        }
    }

    private AppUser.Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return AppUser.Role.USER;
        }
        return AppUser.Role.valueOf(role.trim().toUpperCase());
    }

    public UserSummaryResponse toSummary(AppUser user) {
        return new UserSummaryResponse(
                user.id,
                user.username,
                user.role.name(),
                user.active,
                user.approved,
                user.mustChangePassword,
                hasPassword(user),
                userMailDestinationConfigService.isAnyDestinationConfigured(user.id),
                userEmailAccountRepository.listByUserId(user.id).size(),
                (int) passkeyCount(user.id));
    }

    private void deleteOwnedData(AppUser user) {
        List<String> bridgeIds = userEmailAccountRepository.listByUserId(user.id).stream()
                .map(bridge -> bridge.bridgeId)
                .toList();

        if (!bridgeIds.isEmpty()) {
            sourcePollingSettingRepository.deleteBySourceIds(bridgeIds);
            sourcePollingStateRepository.deleteBySourceIds(bridgeIds);
            sourcePollEventRepository.deleteBySourceIds(bridgeIds);
            importedMessageRepository.deleteBySourceAccountIds(bridgeIds);
            oAuthCredentialService.deleteMicrosoftCredentials(bridgeIds);
            oAuthCredentialService.deleteGoogleCredentials(bridgeIds.stream().map(bridgeId -> "source-google:" + bridgeId).toList());
        }

        oAuthCredentialService.deleteGoogleCredential("user-gmail:" + user.id);
        oAuthCredentialService.deleteMicrosoftCredential("destination-microsoft:" + user.id);
        userEmailAccountRepository.deleteByUserId(user.id);
        userGmailConfigRepository.deleteByUserId(user.id);
        userMailDestinationConfigRepository.deleteByUserId(user.id);
        userPollingSettingRepository.deleteByUserId(user.id);
        userUiPreferenceRepository.deleteByUserId(user.id);
        userPasskeyRepository.deleteByUserId(user.id);
        userSessionService.invalidateUserSessions(user.id);
    }

    private void requirePasswordConfirmation(String password, String confirmPassword) {
        if (confirmPassword == null || password == null || !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password confirmation does not match");
        }
    }

    private String validateNewUsername(String username) {
        String normalized = requireNonBlank(username, "Username");
        if (repository.findByUsername(normalized).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        return normalized;
    }

    private String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private String validatePasswordStrength(String password, String currentPassword) {
        String normalized = requireNonBlank(password, "Password");
        if (normalized.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }
        if (currentPassword != null && normalized.equals(currentPassword)) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        if (!normalized.chars().anyMatch(Character::isUpperCase)) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!normalized.chars().anyMatch(Character::isLowerCase)) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!normalized.chars().anyMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Password must contain at least one number");
        }
        if (normalized.chars().noneMatch(ch -> !Character.isLetterOrDigit(ch))) {
            throw new IllegalArgumentException("Password must contain at least one special character");
        }
        return normalized;
    }

    private String generateUserHandle() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
