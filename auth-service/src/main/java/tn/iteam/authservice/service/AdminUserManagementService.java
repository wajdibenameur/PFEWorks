package tn.iteam.authservice.service;

import feign.FeignException;
import feign.Response;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tn.iteam.authservice.client.KeycloakAdminClient;
import tn.iteam.authservice.config.KeycloakProperties;
import tn.iteam.authservice.dto.*;
import tn.iteam.authservice.exception.KeycloakIntegrationException;
import tn.iteam.authservice.exception.UserAlreadyExistsException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class AdminUserManagementService {

    private static final List<AdminRoleDto> APPLICATION_ROLES = List.of(
            AdminRoleDto.builder().name("SUPERADMIN").label("Superadmin").description("Full platform governance and user administration.").build(),
            AdminRoleDto.builder().name("ADMIN").label("Admin").description("Monitoring administration, tickets and user management.").build(),
            AdminRoleDto.builder().name("SUPPORT").label("Support").description("Operational troubleshooting, ticket follow-up and assisted monitoring.").build(),
            AdminRoleDto.builder().name("VIEWER").label("Viewer").description("Read-only access to dashboards, metrics and incident visibility.").build()
    );

    private final KeycloakAdminClient adminClient;
    private final KeycloakProperties properties;
    private final AdminTokenService adminTokenService;

    public AdminUserManagementService(
            KeycloakAdminClient adminClient,
            KeycloakProperties properties,
            AdminTokenService adminTokenService) {
        this.adminClient = adminClient;
        this.properties = properties;
        this.adminTokenService = adminTokenService;
    }

    public List<AdminManagedUserDto> listUsers() {
        String adminToken = bearerToken();
        try {
            return adminClient.listUsers(properties.getRealm(), adminToken, 0, 200).stream()
                    .filter(user -> StringUtils.hasText(user.getUsername()))
                    .filter(user -> !user.getUsername().startsWith("service-account-"))
                    .map(user -> toAdminUserDto(user, adminToken))
                    .sorted(Comparator.comparing(AdminManagedUserDto::getUsername, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (FeignException e) {
            throw new KeycloakIntegrationException("Failed to list users: " + e.getMessage(), e);
        }
    }

    public List<AdminRoleDto> listRoles() {
        return APPLICATION_ROLES;
    }

    public AdminManagedUserDto createUser(AdminCreateUserRequest request) {
        validateRole(request.getRole());
        String adminToken = bearerToken();

        List<KeycloakUserRepresentation> existing = adminClient.findUserByUsername(
                properties.getRealm(), adminToken, request.getUsername(), true);
        if (!existing.isEmpty()) {
            throw new UserAlreadyExistsException("User '" + request.getUsername() + "' already exists");
        }

        KeycloakUserRepresentation newUser = KeycloakUserRepresentation.builder()
                .username(request.getUsername().trim())
                .email(request.getEmail().trim())
                .firstName(trimToNull(request.getFirstName()))
                .lastName(trimToNull(request.getLastName()))
                .enabled(Boolean.TRUE.equals(request.getEnabled()))
                .emailVerified(true)
                .build();

        Response createResponse;
        try {
            createResponse = adminClient.createUser(properties.getRealm(), adminToken, newUser);
        } catch (FeignException e) {
            if (e.status() == HttpStatus.CONFLICT.value()) {
                throw new UserAlreadyExistsException("User already exists");
            }
            throw new KeycloakIntegrationException("Failed to create user: " + e.getMessage(), e);
        }

        String userId = extractUserIdFromLocation(createResponse);
        resetPassword(userId, request.getPassword(), adminToken);
        replaceApplicationRoles(userId, request.getRole(), adminToken);
        return getUser(userId);
    }

    public AdminManagedUserDto updateUser(String userId, AdminUpdateUserRequest request) {
        validateRole(request.getRole());
        String adminToken = bearerToken();
        KeycloakUserRepresentation existingUser = fetchUser(userId, adminToken);

        KeycloakUserRepresentation updated = KeycloakUserRepresentation.builder()
                .id(existingUser.getId())
                .username(request.getUsername().trim())
                .email(request.getEmail().trim())
                .firstName(trimToNull(request.getFirstName()))
                .lastName(trimToNull(request.getLastName()))
                .enabled(request.getEnabled() != null ? request.getEnabled() : existingUser.getEnabled())
                .emailVerified(Boolean.TRUE.equals(existingUser.getEmailVerified()))
                .build();

        try {
            adminClient.updateUser(properties.getRealm(), userId, adminToken, updated);
        } catch (FeignException e) {
            throw new KeycloakIntegrationException("Failed to update user: " + e.getMessage(), e);
        }

        if (StringUtils.hasText(request.getPassword())) {
            resetPassword(userId, request.getPassword(), adminToken);
        }

        replaceApplicationRoles(userId, request.getRole(), adminToken);
        return getUser(userId);
    }

    public AdminManagedUserDto updateStatus(String userId, boolean enabled) {
        String adminToken = bearerToken();
        KeycloakUserRepresentation existingUser = fetchUser(userId, adminToken);

        KeycloakUserRepresentation updated = KeycloakUserRepresentation.builder()
                .id(existingUser.getId())
                .username(existingUser.getUsername())
                .email(existingUser.getEmail())
                .firstName(existingUser.getFirstName())
                .lastName(existingUser.getLastName())
                .enabled(enabled)
                .emailVerified(Boolean.TRUE.equals(existingUser.getEmailVerified()))
                .build();

        try {
            adminClient.updateUser(properties.getRealm(), userId, adminToken, updated);
        } catch (FeignException e) {
            throw new KeycloakIntegrationException("Failed to update user status: " + e.getMessage(), e);
        }

        return getUser(userId);
    }

    public AdminManagedUserDto getUser(String userId) {
        String adminToken = bearerToken();
        return toAdminUserDto(fetchUser(userId, adminToken), adminToken);
    }

    private void resetPassword(String userId, String password, String adminToken) {
        KeycloakCredentialRepresentation credential = KeycloakCredentialRepresentation.builder()
                .type("password")
                .value(password)
                .temporary(false)
                .build();

        try {
            adminClient.resetPassword(properties.getRealm(), userId, adminToken, credential);
        } catch (FeignException e) {
            throw new KeycloakIntegrationException("Failed to set user password: " + e.getMessage(), e);
        }
    }

    private void replaceApplicationRoles(String userId, String roleName, String adminToken) {
        if (!StringUtils.hasText(roleName)) {
            return;
        }

        try {
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
        } catch (FeignException e) {
            throw new KeycloakIntegrationException("Failed to synchronize user role: " + e.getMessage(), e);
        }
    }

    private AdminManagedUserDto toAdminUserDto(KeycloakUserRepresentation user, String adminToken) {
        List<String> roles = readUserRoles(user.getId(), adminToken);
        return AdminManagedUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .enabled(Boolean.TRUE.equals(user.getEnabled()))
                .roles(roles)
                .build();
    }

    private List<String> readUserRoles(String userId, String adminToken) {
        try {
            return adminClient.getUserRealmRoles(properties.getRealm(), userId, adminToken).stream()
                    .map(KeycloakRoleRepresentation::getName)
                    .filter(this::isApplicationRole)
                    .sorted()
                    .toList();
        } catch (FeignException e) {
            throw new KeycloakIntegrationException("Failed to read user roles: " + e.getMessage(), e);
        }
    }

    private KeycloakUserRepresentation fetchUser(String userId, String adminToken) {
        try {
            return adminClient.getUserById(properties.getRealm(), userId, adminToken);
        } catch (FeignException e) {
            throw new KeycloakIntegrationException("Failed to load user: " + e.getMessage(), e);
        }
    }

    private void validateRole(String roleName) {
        if (!StringUtils.hasText(roleName) || !isApplicationRole(roleName)) {
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String bearerToken() {
        return "Bearer " + adminTokenService.getAdminToken();
    }

    private String extractUserIdFromLocation(Response response) {
        String location = response.headers().getOrDefault("Location",
                response.headers().getOrDefault("location", List.of()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new KeycloakIntegrationException(
                        "User created but Location header missing in Keycloak response"));
        return location.substring(location.lastIndexOf('/') + 1);
    }
}
