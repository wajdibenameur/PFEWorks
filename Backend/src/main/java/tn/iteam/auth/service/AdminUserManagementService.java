package tn.iteam.auth.service;

import feign.FeignException;
import feign.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tn.iteam.auth.client.KeycloakAdminClient;
import tn.iteam.auth.config.KeycloakProperties;
import tn.iteam.auth.dto.*;
import tn.iteam.auth.exception.AuthenticationException;
import tn.iteam.auth.exception.KeycloakIntegrationException;
import tn.iteam.auth.exception.UserAlreadyExistsException;
import tn.iteam.auth.util.KeycloakLocationUtils;
import tn.iteam.domain.User;
import tn.iteam.enums.RoleName;
import tn.iteam.repository.UserRepository;
import tn.iteam.security.AuthenticatedUserService;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AdminUserManagementService {
    private static final Logger log = LoggerFactory.getLogger(AdminUserManagementService.class);
    private static final String PHONE = "phone";
    private static final String POSITION = "position";
    private static final String ADDRESS = "address";
    private static final String CITY = "city";
    private static final String ZIP_CODE = "zipCode";
    private static final String SYSTEM_USERNAME = "SYSTEM";
    private static final String SYSTEM_EMAIL = "system@monitoring.local";

    private static final List<AdminRoleDto> APPLICATION_ROLES = List.of(
            AdminRoleDto.builder().name("SUPERADMIN").label("Superadmin").description("Full platform governance and user administration.").build(),
            AdminRoleDto.builder().name("ADMIN").label("Admin").description("Monitoring administration, tickets and user management.").build(),
            AdminRoleDto.builder().name("SUPPORT").label("Support").description("Operational troubleshooting, ticket follow-up and assisted monitoring.").build(),
            AdminRoleDto.builder().name("VIEWER").label("Viewer").description("Read-only access to dashboards, metrics and incident visibility.").build(),
            AdminRoleDto.builder().name("SYSTEM").label("System").description("Technical service account role for internal automation and notifications.").build()
    );

    private final KeycloakAdminClient adminClient;
    private final KeycloakProperties properties;
    private final AdminTokenService adminTokenService;
    private final UserRepository userRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccountLifecycleEmailService accountLifecycleEmailService;

    public AdminUserManagementService(
            KeycloakAdminClient adminClient,
            KeycloakProperties properties,
            AdminTokenService adminTokenService,
            UserRepository userRepository,
            AuthenticatedUserService authenticatedUserService,
            AccountLifecycleEmailService accountLifecycleEmailService) {
        this.adminClient = adminClient;
        this.properties = properties;
        this.adminTokenService = adminTokenService;
        this.userRepository = userRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.accountLifecycleEmailService = accountLifecycleEmailService;
    }

    public List<AdminManagedUserDto> listUsers() {
        log.info("Listing users from admin management service");
        String adminToken = bearerToken();
        try {
            log.debug("Fetching users from Keycloak");
            List<AdminManagedUserDto> users = adminClient.listUsers(properties.getRealm(), adminToken, 0, 200, false).stream()
                    .filter(user -> StringUtils.hasText(user.getUsername()))
                    .filter(user -> !user.getUsername().startsWith("service-account-"))
                    .map(user -> toAdminUserDto(user, adminToken))
                    .sorted(Comparator.comparing(AdminManagedUserDto::getUsername, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            log.info("Successfully fetched {} users from Keycloak", users.size());
            return users;
        } catch (FeignException e) {
            log.error("Failed to list users from Keycloak", e);
            throw new KeycloakIntegrationException("Failed to list users: " + e.getMessage(), e);
        }
    }

    public List<AdminRoleDto> listRoles() {
        log.info("Listing application roles");
        return APPLICATION_ROLES;
    }

    public AdminManagedUserDto createUser(AdminCreateUserRequest request) {
        log.info("Creating admin-managed user for username: {}", request.getUsername());
        validateRole(request.getRole());
        String adminToken = bearerToken();

        log.debug("Checking if username already exists in Keycloak: {}", request.getUsername());
        List<KeycloakUserRepresentation> existing = adminClient.findUserByUsername(
                properties.getRealm(), adminToken, request.getUsername(), true);
        if (!existing.isEmpty()) {
            log.warn("Create user rejected because username already exists: {}", request.getUsername());
            throw new UserAlreadyExistsException("User '" + request.getUsername() + "' already exists");
        }

        KeycloakUserRepresentation newUser = KeycloakUserRepresentation.builder()
                .username(request.getUsername().trim())
                .email(request.getEmail().trim())
                .firstName(trimToNull(request.getFirstName()))
                .lastName(trimToNull(request.getLastName()))
                .enabled(Boolean.TRUE.equals(request.getEnabled()))
                .emailVerified(true)
                .attributes(buildAdminAttributes(request.getPhone(), request.getPosition()))
                .build();

        Response createResponse;
        try {
            log.debug("Calling Keycloak create user for username: {}", request.getUsername());
            createResponse = adminClient.createUser(properties.getRealm(), adminToken, newUser);
        } catch (FeignException e) {
            if (e.status() == HttpStatus.CONFLICT.value()) {
                log.warn("Keycloak conflict while creating username: {}", request.getUsername());
                throw new UserAlreadyExistsException("User already exists");
            }
            log.error("Keycloak create user failed for username: {}", request.getUsername(), e);
            throw new KeycloakIntegrationException("Failed to create user: " + e.getMessage(), e);
        }

        String userId = KeycloakLocationUtils.extractUserIdFromLocation(createResponse);
        resetPassword(userId, request.getPassword(), adminToken);
        replaceApplicationRoles(userId, request.getRole(), adminToken);
        synchronizeLocalUserSnapshot(
                userId,
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                Boolean.TRUE.equals(request.getEnabled()),
                request.getRole()
        );
        log.info("Admin-managed user created successfully for username: {} with userId: {}", request.getUsername(), userId);
        if (Boolean.TRUE.equals(request.getEnabled())) {
            accountLifecycleEmailService.sendAccountCreatedAndActivated(
                    request.getEmail(),
                    request.getUsername(),
                    normalizeRole(request.getRole())
            );
        }
        return getUser(userId);
    }

    public AdminManagedUserDto updateUser(String userId, AdminUpdateUserRequest request) {
        log.info("Updating admin-managed user with userId: {}", userId);
        validateRole(request.getRole());
        String adminToken = bearerToken();
        String keycloakUserId = resolveKeycloakUserId(userId);
        KeycloakUserRepresentation existingUser = fetchUser(keycloakUserId, adminToken);

        KeycloakUserRepresentation updated = KeycloakUserRepresentation.builder()
                .id(existingUser.getId())
                .username(request.getUsername().trim())
                .email(request.getEmail().trim())
                .firstName(trimToNull(request.getFirstName()))
                .lastName(trimToNull(request.getLastName()))
                .enabled(request.getEnabled() != null ? request.getEnabled() : existingUser.getEnabled())
                .emailVerified(Boolean.TRUE.equals(existingUser.getEmailVerified()))
                .attributes(buildMergedAttributes(existingUser, request.getPhone(), request.getPosition()))
                .build();

        try {
            log.debug("Calling Keycloak update user for userId: {}", keycloakUserId);
            adminClient.updateUser(properties.getRealm(), keycloakUserId, adminToken, updated);
        } catch (FeignException e) {
            log.error("Keycloak update user failed for userId: {}", userId, e);
            throw new KeycloakIntegrationException("Failed to update user: " + e.getMessage(), e);
        }

        if (StringUtils.hasText(request.getPassword())) {
            log.debug("Password reset requested during update for userId: {}", keycloakUserId);
            resetPassword(keycloakUserId, request.getPassword(), adminToken);
        }

        replaceApplicationRoles(keycloakUserId, request.getRole(), adminToken);
        synchronizeLocalUserSnapshot(
                keycloakUserId,
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE.equals(existingUser.getEnabled()),
                request.getRole()
        );
        log.info("Admin-managed user updated successfully for userId: {}", userId);
        return getUser(keycloakUserId);
    }

    @Transactional
    public AdminManagedUserDto updateStatus(String userId, boolean enabled) {
        log.info("Updating status for userId: {} to enabled={}", userId, enabled);
        String adminToken = bearerToken();
        String keycloakUserId = resolveKeycloakUserId(userId);
        KeycloakUserRepresentation existingUser = fetchUser(keycloakUserId, adminToken);
        guardSystemAccountStatusChange(existingUser, enabled);
        boolean previousEnabled = Boolean.TRUE.equals(existingUser.getEnabled());

        KeycloakUserRepresentation updated = KeycloakUserRepresentation.builder()
                .id(existingUser.getId())
                .username(existingUser.getUsername())
                .email(existingUser.getEmail())
                .firstName(existingUser.getFirstName())
                .lastName(existingUser.getLastName())
                .enabled(enabled)
                .emailVerified(Boolean.TRUE.equals(existingUser.getEmailVerified()))
                .attributes(existingUser.getAttributes())
                .build();

        try {
            log.debug("Calling Keycloak status update for userId: {}", keycloakUserId);
            adminClient.updateUser(properties.getRealm(), keycloakUserId, adminToken, updated);
        } catch (FeignException e) {
            log.error("Failed to update status for userId: {}", userId, e);
            throw new KeycloakIntegrationException("Failed to update user status: " + e.getMessage(), e);
        }

        synchronizeLocalEnabledState(keycloakUserId, enabled);
        if (previousEnabled && !enabled) {
            accountLifecycleEmailService.sendAccountDeactivated(existingUser.getEmail(), existingUser.getUsername());
        } else if (!previousEnabled && enabled) {
            accountLifecycleEmailService.sendAccountReactivated(existingUser.getEmail(), existingUser.getUsername());
        }
        log.info("Status updated successfully for userId: {}", userId);
        return getUser(keycloakUserId);
    }

    public AdminManagedUserDto getUser(String userId) {
        log.debug("Fetching admin-managed user details for userId: {}", userId);
        String adminToken = bearerToken();
        String keycloakUserId = resolveKeycloakUserId(userId);
        return toAdminUserDto(fetchUser(keycloakUserId, adminToken), adminToken);
    }

    public AdminManagedUserDto forceLogout(String userId) {
        log.info("Force logout started for userId: {}", userId);
        String adminToken = bearerToken();
        String keycloakUserId = resolveKeycloakUserId(userId);
        fetchUser(keycloakUserId, adminToken);

        try {
            log.debug("Calling Keycloak logout for userId: {}", keycloakUserId);
            adminClient.logoutUser(properties.getRealm(), keycloakUserId, adminToken);
        } catch (FeignException e) {
            log.error("Failed to force logout userId: {}", userId, e);
            throw new KeycloakIntegrationException("Failed to force logout user: " + e.getMessage(), e);
        }

        log.info("Force logout completed for userId: {}", userId);
        return getUser(keycloakUserId);
    }

    @Transactional
    public void deleteUser(String userId) {
        log.info("Deleting user userId={}", userId);
        String adminToken = bearerToken();
        String keycloakUserId = resolveKeycloakUserId(userId);
        KeycloakUserRepresentation existingUser = fetchUser(keycloakUserId, adminToken);
        guardSystemAccountDeletion(existingUser);

        try {
            adminClient.deleteUser(properties.getRealm(), keycloakUserId, adminToken);
        } catch (FeignException e) {
            log.error("Failed to delete Keycloak user for userId={}", userId, e);
            throw new KeycloakIntegrationException("Failed to delete user: " + e.getMessage(), e);
        }

        synchronizeLocalDeletion(keycloakUserId);
        log.info("User deleted successfully userId={} keycloakId={}", userId, keycloakUserId);
    }

    private void resetPassword(String userId, String password, String adminToken) {
        log.debug("Resetting password in Keycloak for userId: {}", userId);
        KeycloakCredentialRepresentation credential = KeycloakCredentialRepresentation.builder()
                .type("password")
                .value(password)
                .temporary(false)
                .build();

        try {
            adminClient.resetPassword(properties.getRealm(), userId, adminToken, credential);
        } catch (FeignException e) {
            log.error("Failed to reset password for userId: {}", userId, e);
            throw new KeycloakIntegrationException("Failed to set user password: " + e.getMessage(), e);
        }
    }

    private void replaceApplicationRoles(String userId, String roleName, String adminToken) {
        if (!StringUtils.hasText(roleName)) {
            log.debug("No application role provided for userId: {}", userId);
            return;
        }

        try {
            log.debug("Synchronizing application roles for userId: {} with target role: {}", userId, normalizeRole(roleName));
            List<KeycloakRoleRepresentation> currentRoles = adminClient.getUserRealmRoles(
                    properties.getRealm(), userId, adminToken);

            List<KeycloakRoleRepresentation> appRolesToRemove = currentRoles.stream()
                    .filter(role -> isApplicationRole(role.getName()))
                    .toList();

            if (!appRolesToRemove.isEmpty()) {
                adminClient.removeRealmRoles(properties.getRealm(), userId, adminToken, appRolesToRemove);
            }

            KeycloakRoleRepresentation targetRole = adminClient.getRealmRole(
                    properties.getRealm(), normalizeRole(roleName), adminToken);
            adminClient.assignRealmRoles(properties.getRealm(), userId, adminToken, List.of(targetRole));
            log.info("Application role synchronized successfully for userId: {}", userId);
        } catch (FeignException e) {
            log.error("Failed to synchronize application role for userId: {}", userId, e);
            throw new KeycloakIntegrationException("Failed to synchronize user role: " + e.getMessage(), e);
        }
    }

    private AdminManagedUserDto toAdminUserDto(KeycloakUserRepresentation user, String adminToken) {
        List<String> roles = readUserRoles(user.getId(), adminToken);
        boolean connected = hasActiveSession(user.getId(), adminToken);
        String phone = firstPresentAttribute(user.getAttributes(), PHONE, "telephone", "mobile");
        String position = firstPresentAttribute(user.getAttributes(), POSITION, "poste", "jobTitle", CITY);
        String address = firstAttribute(user.getAttributes(), ADDRESS);
        String city = firstAttribute(user.getAttributes(), CITY);
        String zipCode = firstAttribute(user.getAttributes(), ZIP_CODE);

        return AdminManagedUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(phone)
                .address(address)
                .city(city)
                .zipCode(zipCode)
                .position(position)
                .enabled(Boolean.TRUE.equals(user.getEnabled()))
                .connected(connected)
                .roles(roles)
                .build();
    }

    private boolean hasActiveSession(String userId, String adminToken) {
        try {
            log.debug("Checking active Keycloak sessions for userId: {}", userId);
            return !adminClient.getUserSessions(properties.getRealm(), userId, adminToken).isEmpty();
        } catch (FeignException e) {
            log.error("Failed to read user sessions for userId: {}", userId, e);
            throw new KeycloakIntegrationException("Failed to read user sessions: " + e.getMessage(), e);
        }
    }

    private List<String> readUserRoles(String userId, String adminToken) {
        try {
            log.debug("Reading Keycloak realm roles for userId: {}", userId);
            return adminClient.getUserRealmRoles(properties.getRealm(), userId, adminToken).stream()
                    .map(KeycloakRoleRepresentation::getName)
                    .filter(this::isApplicationRole)
                    .sorted()
                    .toList();
        } catch (FeignException e) {
            log.error("Failed to read user roles for userId: {}", userId, e);
            throw new KeycloakIntegrationException("Failed to read user roles: " + e.getMessage(), e);
        }
    }

    private KeycloakUserRepresentation fetchUser(String userId, String adminToken) {
        try {
            log.debug("Fetching user by id from Keycloak: {}", userId);
            return adminClient.getUserById(properties.getRealm(), userId, adminToken);
        } catch (FeignException e) {
            log.error("Failed to load user from Keycloak for userId: {}", userId, e);
            throw new KeycloakIntegrationException("Failed to load user: " + e.getMessage(), e);
        }
    }

    private String resolveKeycloakUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new KeycloakIntegrationException("User identifier is required");
        }
        String normalized = userId.trim();
        if (normalized.chars().allMatch(Character::isDigit)) {
            Long localId;
            try {
                localId = Long.parseLong(normalized);
            } catch (NumberFormatException e) {
                throw new KeycloakIntegrationException("Invalid local user identifier: " + userId, e);
            }
            User localUser = userRepository.findById(localId)
                    .orElseThrow(() -> new KeycloakIntegrationException("Local user not found for id: " + userId));
            if (!StringUtils.hasText(localUser.getKeycloakId())) {
                throw new KeycloakIntegrationException("Local user " + userId + " is not linked to a Keycloak account");
            }
            return localUser.getKeycloakId().trim();
        }
        return normalized;
    }

    private void validateRole(String roleName) {
        if (!StringUtils.hasText(roleName) || !isApplicationRole(roleName)) {
            log.warn("Unsupported role received: {}", roleName);
            throw new KeycloakIntegrationException("Unsupported role: " + roleName);
        }
    }

    private boolean isApplicationRole(String roleName) {
        if (!StringUtils.hasText(roleName)) {
            return false;
        }

        String normalized = normalizeRole(roleName);
        return APPLICATION_ROLES.stream().anyMatch(role -> role.getName().equals(normalized));
    }

    private String normalizeRole(String roleName) {
        return roleName.trim().toUpperCase(Locale.ROOT);
    }

    private java.util.Map<String, java.util.List<String>> buildAdminAttributes(String phone, String position) {
        java.util.Map<String, java.util.List<String>> attributes = new java.util.HashMap<>();
        putAttribute(attributes, PHONE, phone);
        putAttribute(attributes, POSITION, position);
        return attributes;
    }

    private java.util.Map<String, java.util.List<String>> buildMergedAttributes(
            KeycloakUserRepresentation existingUser,
            String phone,
            String position
    ) {
        java.util.Map<String, java.util.List<String>> merged = new java.util.HashMap<>();
        if (existingUser.getAttributes() != null) {
            merged.putAll(existingUser.getAttributes());
        }
        putAttribute(merged, PHONE, phone);
        putAttribute(merged, POSITION, position);
        return merged;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void putAttribute(java.util.Map<String, java.util.List<String>> attributes, String key, String value) {
        if (StringUtils.hasText(value)) {
            attributes.put(key, java.util.List.of(value.trim()));
        } else {
            attributes.remove(key);
        }
    }

    private String firstAttribute(java.util.Map<String, java.util.List<String>> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        java.util.List<String> values = attributes.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private String firstPresentAttribute(java.util.Map<String, java.util.List<String>> attributes, String... keys) {
        if (keys == null || keys.length == 0) {
            return null;
        }
        for (String key : keys) {
            String value = firstAttribute(attributes, key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String bearerToken() {
        return "Bearer " + adminTokenService.getAdminToken();
    }

    private void synchronizeLocalEnabledState(String keycloakUserId, boolean enabled) {
        userRepository.findByKeycloakId(keycloakUserId).ifPresentOrElse(localUser -> {
            boolean previous = localUser.isEnabled();
            if (previous != enabled) {
                localUser.setEnabled(enabled);
                userRepository.save(localUser);
            }
            log.info(
                    "LOCAL USER STATUS SYNC username={} keycloakId={} previousEnabled={} updatedEnabled={}",
                    localUser.getUsername(),
                    keycloakUserId,
                    previous,
                    enabled
            );
        }, () -> log.warn(
                "LOCAL USER STATUS SYNC SKIPPED keycloakId={} reason=LOCAL_USER_NOT_FOUND",
                keycloakUserId
        ));
    }

    private void synchronizeLocalDeletion(String keycloakUserId) {
        userRepository.findByKeycloakId(keycloakUserId).ifPresent(localUser -> {
            try {
                userRepository.delete(localUser);
                log.info(
                        "LOCAL USER DELETE SYNC username={} keycloakId={} localUserId={}",
                        localUser.getUsername(),
                        keycloakUserId,
                        localUser.getId()
                );
            } catch (DataIntegrityViolationException ex) {
                log.warn(
                        "LOCAL USER DELETE BLOCKED keycloakId={} localUserId={} reason=DATA_INTEGRITY",
                        keycloakUserId,
                        localUser.getId(),
                        ex
                );
                throw new AuthenticationException(
                        "User deleted from Keycloak but cannot be deleted locally because it is referenced by existing records.",
                        HttpStatus.CONFLICT
                );
            }
        });
    }

    private void guardSystemAccountStatusChange(KeycloakUserRepresentation user, boolean requestedEnabled) {
        if (user == null) {
            return;
        }

        String username = user.getUsername();
        String email = user.getEmail();
        boolean isSystemUser = (username != null && SYSTEM_USERNAME.equalsIgnoreCase(username.trim()))
                || (email != null && SYSTEM_EMAIL.equalsIgnoreCase(email.trim()));

        if (isSystemUser && !requestedEnabled) {
            throw new AuthenticationException("SYSTEM technical account cannot be deactivated", HttpStatus.FORBIDDEN);
        }
    }

    private void guardSystemAccountDeletion(KeycloakUserRepresentation user) {
        if (user == null) {
            return;
        }

        String username = user.getUsername();
        String email = user.getEmail();
        boolean isSystemUser = (username != null && SYSTEM_USERNAME.equalsIgnoreCase(username.trim()))
                || (email != null && SYSTEM_EMAIL.equalsIgnoreCase(email.trim()));

        if (isSystemUser) {
            throw new AuthenticationException("SYSTEM technical account cannot be deleted", HttpStatus.FORBIDDEN);
        }
    }

    private void synchronizeLocalUserSnapshot(
            String keycloakUserId,
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            String roleName
    ) {
        try {
            RoleName resolvedRole = RoleName.valueOf(normalizeRole(roleName));
            authenticatedUserService.synchronizeExternalUser(
                    keycloakUserId,
                    username != null ? username.trim() : null,
                    trimToNull(email),
                    trimToNull(firstName),
                    trimToNull(lastName),
                    enabled,
                    Set.of(resolvedRole)
            );
            log.info("Local user synchronized after Keycloak admin operation for keycloakId={}", keycloakUserId);
        } catch (Exception ex) {
            log.warn(
                    "Local synchronization failed after Keycloak admin operation for keycloakId={}. User may need manual sync.",
                    keycloakUserId,
                    ex
            );
        }
    }
}

