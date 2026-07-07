package tn.iteam.security;

import org.junit.jupiter.api.Test;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionMatrixTest {

    @Test
    void superadminHasAllPermissions() {
        assertThat(RolePermissionMatrix.permissionsFor(RoleName.SUPERADMIN))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Permission.class));
    }

    @Test
    void adminHasExpectedGranularMonitoringPermissions() {
        Set<Permission> adminPermissions = RolePermissionMatrix.permissionsFor(RoleName.ADMIN);

        assertThat(adminPermissions).contains(
                Permission.VIEW_DASHBOARD,
                Permission.VIEW_ZABBIX,
                Permission.VIEW_SNMP,
                Permission.VIEW_CAMERA,
                Permission.VIEW_ACCESS_POINT
        );
    }

    @Test
    void supportHasScopedPermissionsWithoutUserManagement() {
        Set<Permission> supportPermissions = RolePermissionMatrix.permissionsFor(RoleName.SUPPORT);

        assertThat(supportPermissions).contains(
                Permission.VIEW_ZABBIX,
                Permission.VIEW_SNMP,
                Permission.VIEW_CAMERA,
                Permission.VIEW_ACCESS_POINT
        );
        assertThat(supportPermissions).doesNotContain(
                Permission.MANAGE_USERS,
                Permission.MANAGE_PERMISSIONS
        );
    }

    @Test
    void viewerHasMinimalPermissionsOnly() {
        assertThat(RolePermissionMatrix.permissionsFor(RoleName.VIEWER))
                .containsExactlyInAnyOrder(
                        Permission.VIEW_DASHBOARD,
                        Permission.VIEW_TICKETS
                );
    }
}
