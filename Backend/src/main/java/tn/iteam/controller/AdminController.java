package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.dto.AdminUserDTO;
import tn.iteam.dto.SyncAllLocalUsersResponse;
import tn.iteam.dto.SyncLocalUserRequest;
import tn.iteam.dto.UserPermissionMutationRequest;
import tn.iteam.dto.UserPermissionsDTO;
import tn.iteam.service.AdminUserService;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "API d'administration pour les utilisateurs et la gouvernance")
public class AdminController {

    private final AdminUserService adminUserService;

    @GetMapping("/users")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_USERS)")
    @Operation(summary = "Lister les utilisateurs", description = "Retourne les utilisateurs connus de l'application avec leur rôle principal.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs récupérés avec succès")
    public ResponseEntity<List<AdminUserDTO>> getUsers() {
        try {
            return ResponseEntity.ok(adminUserService.getAllUsers());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @PostMapping("/users/sync-local")
    @PreAuthorize("hasAuthority('MANAGE_PERMISSIONS')")
    public ResponseEntity<UserPermissionsDTO> ensureLocalUser(@RequestBody SyncLocalUserRequest request) {
        return ResponseEntity.ok(adminUserService.ensureLocalUser(request));
    }

    @PostMapping("/users/sync-all-local")
    @PreAuthorize("hasAuthority('MANAGE_USERS')")
    public ResponseEntity<SyncAllLocalUsersResponse> syncAllUsersToLocal() {
        return ResponseEntity.ok(adminUserService.syncAllKeycloakUsersToLocal());
    }

    @GetMapping("/users/{userId}/permissions")
    @PreAuthorize("hasAuthority('MANAGE_PERMISSIONS')")
    public ResponseEntity<UserPermissionsDTO> getUserPermissions(@PathVariable Long userId) {
        return ResponseEntity.ok(adminUserService.getUserPermissions(userId));
    }

    @PostMapping("/users/{userId}/permissions/grant")
    @PreAuthorize("hasAuthority('MANAGE_PERMISSIONS')")
    public ResponseEntity<UserPermissionsDTO> grantPermission(
            @PathVariable Long userId,
            @RequestBody UserPermissionMutationRequest request) {
        return ResponseEntity.ok(adminUserService.grantPermission(userId, request.getPermission()));
    }

    @PostMapping("/users/{userId}/permissions/revoke")
    @PreAuthorize("hasAuthority('MANAGE_PERMISSIONS')")
    public ResponseEntity<UserPermissionsDTO> revokePermission(
            @PathVariable Long userId,
            @RequestBody UserPermissionMutationRequest request) {
        return ResponseEntity.ok(adminUserService.revokePermission(userId, request.getPermission()));
    }

    @DeleteMapping("/users/{userId}/permissions/grant/{permission}")
    @PreAuthorize("hasAuthority('MANAGE_PERMISSIONS')")
    public ResponseEntity<UserPermissionsDTO> removeGrantedPermission(
            @PathVariable Long userId,
            @PathVariable String permission) {
        return ResponseEntity.ok(adminUserService.removeGrantedPermission(userId, permission));
    }

    @DeleteMapping("/users/{userId}/permissions/revoke/{permission}")
    @PreAuthorize("hasAuthority('MANAGE_PERMISSIONS')")
    public ResponseEntity<UserPermissionsDTO> removeRevokedPermission(
            @PathVariable Long userId,
            @PathVariable String permission) {
        return ResponseEntity.ok(adminUserService.removeRevokedPermission(userId, permission));
    }
}
