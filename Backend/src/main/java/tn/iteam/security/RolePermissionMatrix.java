package tn.iteam.security;

import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Central source of truth for translating Keycloak realm roles into
 * monitoring/ticketing permissions inside Backend.
 */
public final class RolePermissionMatrix {

    private static final Map<RoleName, Set<Permission>> MATRIX = buildMatrix();

    private RolePermissionMatrix() {
    }

    public static Set<Permission> permissionsFor(RoleName roleName) {
        if (roleName == null) {
            return Set.of();
        }
        return MATRIX.getOrDefault(roleName, Set.of());
    }

    public static Map<RoleName, Set<Permission>> asMap() {
        return MATRIX;
    }

    private static Map<RoleName, Set<Permission>> buildMatrix() {
        EnumMap<RoleName, Set<Permission>> matrix = new EnumMap<>(RoleName.class);

        matrix.put(RoleName.SUPERADMIN, Set.copyOf(EnumSet.allOf(Permission.class)));
        matrix.put(RoleName.ADMIN, Set.copyOf(EnumSet.of(
                Permission.VIEW_DASHBOARD,
                Permission.VIEW_METRICS,
                Permission.VIEW_ALERTS,
                Permission.VIEW_LOGS,
                Permission.EXPORT_DASHBOARD,
                Permission.REFRESH_DASHBOARD,
                Permission.VIEW_HOSTS,
                Permission.MANAGE_HOSTS,
                Permission.EDIT_HOST,
                Permission.VIEW_TICKETS,
                Permission.VIEW_ALL_TICKETS,
                Permission.VIEW_ASSIGNED_TICKETS,
                Permission.CREATE_TICKET,
                Permission.EDIT_TICKET,
                Permission.ASSIGN_TICKET,
                Permission.VALIDATE_TICKET,
                Permission.ADD_COMMENT,
                Permission.EDIT_COMMENT,
                Permission.VIEW_USERS,
                Permission.VIEW_ROLES
        )));
        matrix.put(RoleName.SUPPORT, Set.copyOf(EnumSet.of(
                Permission.VIEW_DASHBOARD,
                Permission.VIEW_METRICS,
                Permission.VIEW_ALERTS,
                Permission.VIEW_LOGS,
                Permission.VIEW_HOSTS,
                Permission.VIEW_TICKETS,
                Permission.VIEW_ASSIGNED_TICKETS,
                Permission.CREATE_TICKET,
                Permission.EDIT_TICKET,
                Permission.ADD_COMMENT,
                Permission.EDIT_COMMENT
        )));
        matrix.put(RoleName.VIEWER, Set.copyOf(EnumSet.of(
                Permission.VIEW_DASHBOARD,
                Permission.VIEW_METRICS,
                Permission.VIEW_ALERTS,
                Permission.VIEW_LOGS,
                Permission.VIEW_HOSTS,
                Permission.VIEW_TICKETS
        )));

        return Collections.unmodifiableMap(matrix);
    }
}
