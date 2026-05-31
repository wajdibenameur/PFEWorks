package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tn.iteam.auth.client.KeycloakAdminClient;
import tn.iteam.auth.config.KeycloakProperties;
import tn.iteam.auth.dto.KeycloakRoleRepresentation;
import tn.iteam.auth.dto.KeycloakUserRepresentation;
import tn.iteam.auth.service.AdminTokenService;
import tn.iteam.domain.User;
import tn.iteam.dto.AdminUserDTO;
import tn.iteam.dto.SyncAllLocalUsersResponse;
import tn.iteam.dto.SyncLocalUserRequest;
import tn.iteam.dto.UserPermissionsDTO;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;
import tn.iteam.exception.TicketingException;
import tn.iteam.repository.UserRepository;
import tn.iteam.security.AuthenticatedUserService;
import tn.iteam.security.EffectiveUserPermissionService;
import tn.iteam.security.KeycloakRolePermissionService;
import tn.iteam.service.AdminUserService;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    private static final Logger log = LoggerFactory.getLogger(AdminUserServiceImpl.class);
    private static final Set<Permission> SELF_CRITICAL_PERMISSIONS = EnumSet.of(
            Permission.MANAGE_PERMISSIONS,
            Permission.MANAGE_USERS,
            Permission.MANAGE_ROLES,
            Permission.VIEW_PERMISSIONS,
            Permission.EDIT_USER,
            Permission.DELETE_USER,
            Permission.ACTIVATE_USER,
            Permission.DEACTIVATE_USER,
            Permission.ASSIGN_ROLE_TO_USER,
            Permission.REMOVE_ROLE_FROM_USER
    );

    private final UserRepository userRepository;
    private final KeycloakRolePermissionService rolePermissionService;
    private final EffectiveUserPermissionService effectiveUserPermissionService;
    private final AuthenticatedUserService authenticatedUserService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakProperties keycloakProperties;
    private final AdminTokenService adminTokenService;

    /**
     * Returns a local support view of synchronized users persisted by Backend.
     * auth-service and Keycloak remain the official source of truth for user and role management.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AdminUserDTO> getAllUsers() {
        String adminToken = null;
        try {
            adminToken = "Bearer " + adminTokenService.getAdminToken();
        } catch (Exception ex) {
            log.warn("Admin token unavailable while listing local users; keycloak enabled status logs will be skipped");
        }

        final String bearerToken = adminToken;
        return userRepository.findAllByOrderByUsernameAsc().stream()
                .map(user -> {
                    try {
                        return toAdminUserDto(user, bearerToken);
                    } catch (Exception ex) {
                        log.warn("Failed to map local admin user id={} username={}",
                                user.getId(),
                                user.getUsername(),
                                ex);
                        return AdminUserDTO.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .email(user.getEmail())
                                .phone(null)
                                .position(null)
                                .role("UNASSIGNED")
                                .enabled(user.isEnabled())
                                .rolePermissions(Set.of())
                                .extraPermissions(Set.of())
                                .revokedPermissions(Set.of())
                                .effectivePermissions(Set.of())
                                .build();
                    }
                })
                .toList();
    }

    @Override
    public UserPermissionsDTO ensureLocalUser(SyncLocalUserRequest request) {
        if (request == null || isBlank(request.getUsername())) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_USER", "Username is required");
        }

        Set<RoleName> roles = Set.of(resolveRoleName(request.getRole()));
        User user = authenticatedUserService.synchronizeUser(
                request.getUsername().trim(),
                normalizeBlank(request.getEmail()),
                roles
        );
        return toPermissionsDto(user);
    }

    @Override
    public SyncAllLocalUsersResponse syncAllKeycloakUsersToLocal() {
        final int pageSize = 200;
        int first = 0;
        int total = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;

        String bearerToken = "Bearer " + adminTokenService.getAdminToken();

        while (true) {
            List<KeycloakUserRepresentation> page = keycloakAdminClient.listUsers(
                    keycloakProperties.getRealm(),
                    bearerToken,
                    first,
                    pageSize,
                    false
            );
            if (page == null || page.isEmpty()) {
                break;
            }

            for (KeycloakUserRepresentation user : page) {
                total++;
                String username = normalizeBlank(user.getUsername());
                if (!StringUtils.hasText(username) || username.startsWith("service-account-")) {
                    skipped++;
                    continue;
                }

                String keycloakId = normalizeBlank(user.getId());
                if (!StringUtils.hasText(keycloakId)) {
                    skipped++;
                    continue;
                }

                try {
                    boolean existed = userRepository.findByKeycloakId(keycloakId).isPresent();
                    Set<RoleName> roleNames = resolveRoleNamesFromKeycloak(keycloakId, bearerToken);

                    authenticatedUserService.synchronizeExternalUser(
                            keycloakId,
                            username,
                            normalizeBlank(user.getEmail()),
                            normalizeBlank(user.getFirstName()),
                            normalizeBlank(user.getLastName()),
                            user.getEnabled() == null || Boolean.TRUE.equals(user.getEnabled()),
                            roleNames
                    );

                    if (existed) {
                        updated++;
                    } else {
                        created++;
                    }
                } catch (Exception ex) {
                    failed++;
                    log.warn("Failed to synchronize Keycloak user id={} username={}", user.getId(), user.getUsername(), ex);
                }
            }

            if (page.size() < pageSize) {
                break;
            }
            first += pageSize;
        }

        return SyncAllLocalUsersResponse.builder()
                .totalKeycloakUsers(total)
                .synchronizedUsers(created + updated)
                .createdUsers(created)
                .updatedUsers(updated)
                .skippedUsers(skipped)
                .failedUsers(failed)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserPermissionsDTO getUserPermissions(Long userId) {
        return toPermissionsDto(requireUser(userId));
    }

    @Override
    @Transactional
    public UserPermissionsDTO grantPermission(Long userId, String permission) {
        User user = requireUser(userId);
        Permission resolved = requirePermission(permission);
        guardSelfCriticalPermissionChange(user, resolved);

        user.getRevokedPermissions().remove(resolved);
        user.getExtraPermissions().add(resolved);
        return toPermissionsDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserPermissionsDTO revokePermission(Long userId, String permission) {
        User user = requireUser(userId);
        Permission resolved = requirePermission(permission);
        guardSelfCriticalPermissionChange(user, resolved);

        user.getExtraPermissions().remove(resolved);
        user.getRevokedPermissions().add(resolved);
        return toPermissionsDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserPermissionsDTO removeGrantedPermission(Long userId, String permission) {
        User user = requireUser(userId);
        Permission resolved = requirePermission(permission);
        guardSelfCriticalPermissionChange(user, resolved);

        user.getExtraPermissions().remove(resolved);
        return toPermissionsDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserPermissionsDTO removeRevokedPermission(Long userId, String permission) {
        User user = requireUser(userId);
        Permission resolved = requirePermission(permission);
        guardSelfCriticalPermissionChange(user, resolved);

        user.getRevokedPermissions().remove(resolved);
        return toPermissionsDto(userRepository.save(user));
    }

    private AdminUserDTO toAdminUserDto(User user, String adminToken) {
        UserPermissionsDTO permissions = toPermissionsDto(user);
        Boolean localEnabled = user.isEnabled();
        KeycloakUserRepresentation keycloakUser = fetchKeycloakUser(user, adminToken);
        Boolean keycloakEnabled = keycloakUser != null ? keycloakUser.getEnabled() : null;
        String phone = extractAttribute(keycloakUser, "phone", "mobile", "phone_number", "telephone");
        String position = extractAttribute(keycloakUser, "position", "poste", "jobTitle", "job_title", "title");

        log.info(
                "ADMIN USERS DTO TRACE username={} localEnabled={} keycloakEnabled={} dtoEnabled={} hasPhone={} hasPosition={}",
                user.getUsername(),
                localEnabled,
                keycloakEnabled,
                localEnabled,
                phone != null,
                position != null
        );

        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(phone)
                .position(position)
                .role(resolveRoleName(user))
                .enabled(localEnabled)
                .rolePermissions(permissions.getRolePermissions())
                .extraPermissions(permissions.getExtraPermissions())
                .revokedPermissions(permissions.getRevokedPermissions())
                .effectivePermissions(permissions.getEffectivePermissions())
                .build();
    }

    private KeycloakUserRepresentation fetchKeycloakUser(User user, String adminToken) {
        if (user == null || !StringUtils.hasText(user.getKeycloakId()) || !StringUtils.hasText(adminToken)) {
            return null;
        }

        try {
            return keycloakAdminClient.getUserById(
                    keycloakProperties.getRealm(),
                    user.getKeycloakId().trim(),
                    adminToken
            );
        } catch (Exception ex) {
            log.debug(
                    "Unable to resolve keycloak profile for username={} keycloakId={}",
                    user.getUsername(),
                    user.getKeycloakId(),
                    ex
            );
            return null;
        }
    }

    private String extractAttribute(KeycloakUserRepresentation keycloakUser, String... keys) {
        if (keycloakUser == null || keycloakUser.getAttributes() == null || keys == null) {
            return null;
        }
        Map<String, List<String>> attributes = keycloakUser.getAttributes();
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            List<String> values = attributes.get(key);
            if (values == null || values.isEmpty()) {
                continue;
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return null;
    }

    private UserPermissionsDTO toPermissionsDto(User user) {
        Set<RoleName> roles = resolveRoleNames(user);
        Set<Permission> rolePermissions = effectiveUserPermissionService.rolePermissions(roles);
        Set<Permission> effectivePermissions = effectiveUserPermissionService.resolveEffectivePermissions(roles, user);

        return UserPermissionsDTO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .role(resolveRoleName(user))
                .rolePermissions(toSortedNames(rolePermissions))
                .extraPermissions(toSortedNames(user.getExtraPermissions()))
                .revokedPermissions(toSortedNames(user.getRevokedPermissions()))
                .effectivePermissions(toSortedNames(effectivePermissions))
                .build();
    }

    private User requireUser(Long userId) {
        return userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new TicketingException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private Permission requirePermission(String rawPermission) {
        try {
            return Permission.valueOf(rawPermission.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_PERMISSION", "Permission does not exist");
        }
    }

    private RoleName resolveRoleName(String rawRole) {
        try {
            return RoleName.valueOf(rawRole.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Role does not exist");
        }
    }

    private Set<RoleName> resolveRoleNamesFromKeycloak(String keycloakUserId, String bearerToken) {
        try {
            List<KeycloakRoleRepresentation> roleRepresentations = keycloakAdminClient.getUserRealmRoles(
                    keycloakProperties.getRealm(),
                    keycloakUserId,
                    bearerToken
            );
            List<String> roleNames = new ArrayList<>();
            if (roleRepresentations != null) {
                for (KeycloakRoleRepresentation roleRepresentation : roleRepresentations) {
                    if (roleRepresentation != null && StringUtils.hasText(roleRepresentation.getName())) {
                        roleNames.add(roleRepresentation.getName());
                    }
                }
            }
            return rolePermissionService.parseRoles(roleNames);
        } catch (FeignException ex) {
            log.warn("Failed to load Keycloak roles for userId={} - synchronizing without role update", keycloakUserId);
            return Set.of();
        }
    }

    private void guardSelfCriticalPermissionChange(User targetUser, Permission permission) {
        String currentUsername = authenticatedUserService.getCurrentUsername();
        if (currentUsername != null
                && currentUsername.equalsIgnoreCase(safeValue(targetUser.getUsername()))
                && SELF_CRITICAL_PERMISSIONS.contains(permission)) {
            throw new TicketingException(
                    HttpStatus.FORBIDDEN,
                    "SELF_PERMISSION_CHANGE_FORBIDDEN",
                    "You cannot modify your own critical permissions"
            );
        }
    }

    private Set<RoleName> resolveRoleNames(User user) {
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            return user.getRoles().stream()
                    .filter(role -> role != null && role.getName() != null)
                    .map(role -> role.getName())
                    .collect(java.util.stream.Collectors.toSet());
        }
        if (user.getRole() != null && user.getRole().getName() != null) {
            return Set.of(user.getRole().getName());
        }
        return Set.of();
    }

    private String resolveRoleName(User user) {
        return user.getRole() != null && user.getRole().getName() != null
                ? user.getRole().getName().name()
                : "UNASSIGNED";
    }

    private Set<String> toSortedNames(Set<Permission> permissions) {
        TreeSet<String> names = new TreeSet<>();
        if (permissions != null) {
            permissions.stream().map(Permission::name).forEach(names::add);
        }
        return Set.copyOf(names);
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private String normalizeBlank(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
