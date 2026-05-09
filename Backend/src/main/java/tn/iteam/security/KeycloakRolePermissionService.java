package tn.iteam.security;

import org.springframework.stereotype.Service;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Service
public class KeycloakRolePermissionService {

    public Set<Permission> permissionsFor(RoleName roleName) {
        return RolePermissionMatrix.permissionsFor(roleName);
    }

    public Set<Permission> permissionsForRoles(Set<RoleName> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Set.of();
        }

        EnumSet<Permission> merged = EnumSet.noneOf(Permission.class);
        for (RoleName roleName : roleNames) {
            merged.addAll(permissionsFor(roleName));
        }
        return Set.copyOf(merged);
    }

    public Set<RoleName> parseRoles(Iterable<String> roles) {
        if (roles == null) {
            return Set.of();
        }

        EnumSet<RoleName> resolved = EnumSet.noneOf(RoleName.class);
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            try {
                resolved.add(RoleName.valueOf(role.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown realm roles that are irrelevant for this application.
            }
        }
        return Set.copyOf(resolved);
    }

    public RoleName highestPrivilegeRole(Set<RoleName> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return null;
        }

        return Arrays.stream(RoleName.values())
                .filter(roleNames::contains)
                .findFirst()
                .orElse(null);
    }
}
