package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tn.iteam.domain.User;
import tn.iteam.dto.AdminUserDTO;
import tn.iteam.dto.UserPermissionsDTO;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;
import tn.iteam.exception.TicketingException;
import tn.iteam.repository.UserRepository;
import tn.iteam.security.AuthenticatedUserService;
import tn.iteam.security.EffectiveUserPermissionService;
import tn.iteam.security.KeycloakRolePermissionService;
import tn.iteam.service.AdminUserService;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

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

    /**
     * Returns a local support view of synchronized users persisted by Backend.
     * auth-service and Keycloak remain the official source of truth for user and role management.
     */
    @Override
    public List<AdminUserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(user -> safeValue(user.getUsername()), String.CASE_INSENSITIVE_ORDER))
                .map(this::toAdminUserDto)
                .toList();
    }

    @Override
    public UserPermissionsDTO getUserPermissions(Long userId) {
        return toPermissionsDto(requireUser(userId));
    }

    @Override
    public UserPermissionsDTO grantPermission(Long userId, String permission) {
        User user = requireUser(userId);
        Permission resolved = requirePermission(permission);
        guardSelfCriticalPermissionChange(user, resolved);

        user.getRevokedPermissions().remove(resolved);
        user.getExtraPermissions().add(resolved);
        return toPermissionsDto(userRepository.save(user));
    }

    @Override
    public UserPermissionsDTO revokePermission(Long userId, String permission) {
        User user = requireUser(userId);
        Permission resolved = requirePermission(permission);
        guardSelfCriticalPermissionChange(user, resolved);

        user.getExtraPermissions().remove(resolved);
        user.getRevokedPermissions().add(resolved);
        return toPermissionsDto(userRepository.save(user));
    }

    @Override
    public UserPermissionsDTO removeGrantedPermission(Long userId, String permission) {
        User user = requireUser(userId);
        Permission resolved = requirePermission(permission);
        guardSelfCriticalPermissionChange(user, resolved);

        user.getExtraPermissions().remove(resolved);
        return toPermissionsDto(userRepository.save(user));
    }

    @Override
    public UserPermissionsDTO removeRevokedPermission(Long userId, String permission) {
        User user = requireUser(userId);
        Permission resolved = requirePermission(permission);
        guardSelfCriticalPermissionChange(user, resolved);

        user.getRevokedPermissions().remove(resolved);
        return toPermissionsDto(userRepository.save(user));
    }

    private AdminUserDTO toAdminUserDto(User user) {
        UserPermissionsDTO permissions = toPermissionsDto(user);
        return AdminUserDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(resolveRoleName(user))
                .rolePermissions(permissions.getRolePermissions())
                .extraPermissions(permissions.getExtraPermissions())
                .revokedPermissions(permissions.getRevokedPermissions())
                .effectivePermissions(permissions.getEffectivePermissions())
                .build();
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
        return userRepository.findById(userId)
                .orElseThrow(() -> new TicketingException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
    }

    private Permission requirePermission(String rawPermission) {
        try {
            return Permission.valueOf(rawPermission.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_PERMISSION", "Permission does not exist");
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
        if (user.getRole() == null || user.getRole().getName() == null) {
            return Set.of();
        }
        return Set.of(user.getRole().getName());
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
}
