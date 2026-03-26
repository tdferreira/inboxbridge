package dev.inboxbridge.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.inboxbridge.dto.CreateUserRequest;
import dev.inboxbridge.dto.RegisterUserRequest;
import dev.inboxbridge.dto.UpdateUserRequest;
import dev.inboxbridge.dto.UserSummaryResponse;
import dev.inboxbridge.persistence.AppUser;
import dev.inboxbridge.persistence.AppUserRepository;
import dev.inboxbridge.persistence.UserBridgeRepository;
import dev.inboxbridge.persistence.UserGmailConfigRepository;
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
    UserBridgeRepository userBridgeRepository;

    void onStartup(@Observes StartupEvent event) {
        ensureBootstrapAdmin();
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
        user.passwordHash = passwordHashService.hash(validatePasswordStrength(request.password(), null));
        user.role = parseRole(request.role());
        user.mustChangePassword = true;
        user.active = true;
        user.approved = true;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        repository.persist(user);
        return user;
    }

    @Transactional
    public AppUser registerUser(RegisterUserRequest request) {
        String username = validateNewUsername(request.username());
        AppUser user = new AppUser();
        user.username = username;
        user.passwordHash = passwordHashService.hash(validatePasswordStrength(request.password(), null));
        user.role = AppUser.Role.USER;
        user.mustChangePassword = false;
        user.active = false;
        user.approved = false;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        repository.persist(user);
        return user;
    }

    @Transactional
    public void changePassword(AppUser user, String currentPassword, String newPassword, String confirmNewPassword) {
        AppUser managedUser = repository.findByIdOptional(user.id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        if (!passwordMatches(managedUser, currentPassword)) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (confirmNewPassword == null || !newPassword.equals(confirmNewPassword)) {
            throw new IllegalArgumentException("New password confirmation does not match");
        }
        String validatedPassword = validatePasswordStrength(newPassword, currentPassword);
        managedUser.passwordHash = passwordHashService.hash(validatedPassword);
        managedUser.mustChangePassword = false;
        managedUser.updatedAt = Instant.now();
    }

    public boolean passwordMatches(AppUser user, String rawPassword) {
        return passwordHashService.matches(rawPassword, user.passwordHash);
    }

    @Transactional
    public void ensureBootstrapAdmin() {
        if (repository.count() > 0) {
            return;
        }
        AppUser admin = new AppUser();
        admin.username = "admin";
        admin.passwordHash = passwordHashService.hash("nimda");
        admin.role = AppUser.Role.ADMIN;
        admin.mustChangePassword = true;
        admin.active = true;
        admin.approved = true;
        admin.createdAt = Instant.now();
        admin.updatedAt = admin.createdAt;
        repository.persist(admin);
    }

    @Transactional
    public AppUser updateUser(Long userId, UpdateUserRequest request) {
        AppUser user = repository.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id"));
        AppUser.Role newRole = request.role() == null || request.role().isBlank() ? user.role : parseRole(request.role());
        boolean newActive = request.active() == null ? user.active : request.active();
        boolean newApproved = request.approved() == null ? user.approved : request.approved();

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
                userGmailConfigRepository.findByUserId(user.id).isPresent(),
                userBridgeRepository.listByUserId(user.id).size());
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
}
