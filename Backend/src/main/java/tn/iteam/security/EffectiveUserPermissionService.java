package tn.iteam.security;

import org.springframework.stereotype.Service;
import tn.iteam.domain.User;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;

import java.util.EnumSet;
import java.util.Set;

@Service
public class EffectiveUserPermissionService {

    private final KeycloakRolePermissionService rolePermissionService;

    public EffectiveUserPermissionService(KeycloakRolePermissionService rolePermissionService) {
        this.rolePermissionService = rolePermissionService;
    }

    public Set<Permission> resolveEffectivePermissions(Set<RoleName> roles, User user) {
        if (roles != null && roles.contains(RoleName.SUPERADMIN)) {
            return Set.copyOf(EnumSet.allOf(Permission.class));
        }

        EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
        Set<RoleName> resolvedRoles = roles;
        if ((resolvedRoles == null || resolvedRoles.isEmpty()) && user != null) {
            resolvedRoles = user.getRoles() == null
                    ? Set.of()
                    : user.getRoles().stream()
                    .filter(role -> role != null && role.getName() != null)
                    .map(role -> role.getName())
                    .collect(java.util.stream.Collectors.toSet());

            if ((resolvedRoles == null || resolvedRoles.isEmpty())
                    && user.getRole() != null
                    && user.getRole().getName() != null) {
                resolvedRoles = Set.of(user.getRole().getName());
            }
        }

        if (resolvedRoles != null && !resolvedRoles.isEmpty()) {
            effective.addAll(rolePermissionService.permissionsForRoles(resolvedRoles));
        }

        if (user != null) {
            if (user.getExtraPermissions() != null) {
                effective.addAll(user.getExtraPermissions());
            }
            if (user.getRevokedPermissions() != null) {
                effective.removeAll(user.getRevokedPermissions());
            }
        }

        return Set.copyOf(effective);
    }

    public Set<Permission> rolePermissions(Set<RoleName> roles) {
        if (roles != null && roles.contains(RoleName.SUPERADMIN)) {
            return Set.copyOf(EnumSet.allOf(Permission.class));
        }
        return rolePermissionService.permissionsForRoles(roles);
    }
}
