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
        if (roles != null && !roles.isEmpty()) {
            effective.addAll(rolePermissionService.permissionsForRoles(roles));
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
