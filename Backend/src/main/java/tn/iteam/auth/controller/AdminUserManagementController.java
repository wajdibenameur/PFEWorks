package tn.iteam.auth.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tn.iteam.auth.dto.*;
import tn.iteam.auth.service.AdminUserManagementService;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminUserManagementController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserManagementController.class);

    private final AdminUserManagementService adminUserManagementService;

    public AdminUserManagementController(AdminUserManagementService adminUserManagementService) {
        this.adminUserManagementService = adminUserManagementService;
    }

    @GetMapping("/roles")
    public ResponseEntity<java.util.List<AdminRoleDto>> getRoles() {
        log.info("Admin requested roles list by actor: {}", currentActor());
        java.util.List<AdminRoleDto> roles = adminUserManagementService.listRoles();
        log.info("Roles list returned successfully with {} entries", roles.size());
        return ResponseEntity.ok(roles);
    }

    @PostMapping("/users")
    public ResponseEntity<AdminManagedUserDto> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        log.info("Admin create user request received for username: {} by actor: {}", request.getUsername(), currentActor());
        AdminManagedUserDto response = adminUserManagementService.createUser(request);
        log.info("User {} created successfully with userId: {}", response.getUsername(), response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<AdminManagedUserDto> updateUser(
            @PathVariable("userId") String userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        log.info("Admin update user request received for userId: {} by actor: {}", userId, currentActor());
        AdminManagedUserDto response = adminUserManagementService.updateUser(userId, request);
        log.info("User {} updated successfully", userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<AdminManagedUserDto> updateUserStatus(
            @PathVariable("userId") String userId,
            @Valid @RequestBody AdminUpdateUserStatusRequest request) {
        log.info("Admin status update request received for userId: {} by actor: {}", userId, currentActor());
        AdminManagedUserDto response = adminUserManagementService.updateStatus(userId, request.getEnabled());
        log.info("User {} status updated successfully", userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/force-logout")
    public ResponseEntity<AdminManagedUserDto> forceLogout(
            @PathVariable("userId") String userId) {
        log.info("Force logout requested for userId: {} by actor: {}", userId, currentActor());
        AdminManagedUserDto response = adminUserManagementService.forceLogout(userId);
        log.info("Force logout completed for userId: {}", userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable("userId") String userId) {
        log.info("Delete user requested for userId: {} by actor: {}", userId, currentActor());
        adminUserManagementService.deleteUser(userId);
        log.info("Delete user completed for userId: {}", userId);
        return ResponseEntity.noContent().build();
    }

    private String currentActor() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return "anonymous";
        }
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}

