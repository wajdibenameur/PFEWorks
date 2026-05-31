package tn.iteam.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class PermissionConsistencyStartupValidator {

    private static final Logger log = LoggerFactory.getLogger(PermissionConsistencyStartupValidator.class);

    @PostConstruct
    void validate() {
        Map<RoleName, Set<Permission>> matrix = RolePermissionMatrix.asMap();
        Set<Permission> enumPermissions = EnumSet.allOf(Permission.class);
        Set<Permission> matrixPermissions = EnumSet.noneOf(Permission.class);

        for (RoleName roleName : RoleName.values()) {
            Set<Permission> rolePermissions = matrix.get(roleName);
            if (rolePermissions == null) {
                throw new IllegalStateException("RBAC matrix missing role entry: " + roleName);
            }
            if (rolePermissions.stream().anyMatch(permission -> permission == null)) {
                throw new IllegalStateException("RBAC matrix has null permission for role: " + roleName);
            }
            matrixPermissions.addAll(rolePermissions);
        }

        if (!enumPermissions.containsAll(matrixPermissions)) {
            throw new IllegalStateException("RBAC matrix contains unknown permissions: " + matrixPermissions);
        }

        log.info(
                "RBAC permission consistency check passed: roles={}, enumPermissions={}, mappedPermissions={}",
                matrix.size(),
                enumPermissions.size(),
                matrixPermissions.size()
        );
    }
}
