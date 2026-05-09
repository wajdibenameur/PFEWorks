package tn.iteam.service;

import tn.iteam.dto.AdminUserDTO;
import tn.iteam.dto.UserPermissionsDTO;

import java.util.List;

/**
 * Local support view only. The official source of users and roles is
 * auth-service backed by Keycloak.
 */
public interface AdminUserService {

    List<AdminUserDTO> getAllUsers();

    UserPermissionsDTO getUserPermissions(Long userId);

    UserPermissionsDTO grantPermission(Long userId, String permission);

    UserPermissionsDTO revokePermission(Long userId, String permission);

    UserPermissionsDTO removeGrantedPermission(Long userId, String permission);

    UserPermissionsDTO removeRevokedPermission(Long userId, String permission);
}
