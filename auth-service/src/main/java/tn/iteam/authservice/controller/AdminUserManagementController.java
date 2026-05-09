package tn.iteam.authservice.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.iteam.authservice.dto.*;
import tn.iteam.authservice.service.AdminUserManagementService;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyAuthority('SUPERADMIN','ADMIN')")
public class AdminUserManagementController {

    private final AdminUserManagementService adminUserManagementService;

    public AdminUserManagementController(AdminUserManagementService adminUserManagementService) {
        this.adminUserManagementService = adminUserManagementService;
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminManagedUserDto>> getUsers() {
        return ResponseEntity.ok(adminUserManagementService.listUsers());
    }

    @GetMapping("/roles")
    public ResponseEntity<List<AdminRoleDto>> getRoles() {
        return ResponseEntity.ok(adminUserManagementService.listRoles());
    }

    @PostMapping("/users")
    public ResponseEntity<AdminManagedUserDto> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserManagementService.createUser(request));
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<AdminManagedUserDto> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(adminUserManagementService.updateUser(userId, request));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<AdminManagedUserDto> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody AdminUpdateUserStatusRequest request) {
        return ResponseEntity.ok(adminUserManagementService.updateStatus(userId, request.getEnabled()));
    }
}
