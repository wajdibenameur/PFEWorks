package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.domain.User;
import tn.iteam.dto.CurrentUserPermissionsResponse;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;
import tn.iteam.security.AuthenticatedUserService;
import tn.iteam.security.EffectiveUserPermissionService;

import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/api/auth/me")
@RequiredArgsConstructor
@Tag(name = "Current User", description = "API pour consulter le contexte de securite de l utilisateur courant")
public class CurrentUserController {

    private final AuthenticatedUserService authenticatedUserService;
    private final EffectiveUserPermissionService effectiveUserPermissionService;

    @GetMapping("/permissions")
    @Operation(
            summary = "Recuperer les permissions effectives de l utilisateur courant",
            description = "Retourne les roles applicatifs et les permissions effectives resolues par le backend."
    )
    @ApiResponse(responseCode = "200", description = "Permissions effectives recuperees avec succes")
    public ResponseEntity<CurrentUserPermissionsResponse> getCurrentUserPermissions() {
        Set<RoleName> roles = authenticatedUserService.getCurrentRoles();
        User user = authenticatedUserService.getCurrentUser();
        Set<Permission> effectivePermissions = effectiveUserPermissionService.resolveEffectivePermissions(roles, user);

        CurrentUserPermissionsResponse response = new CurrentUserPermissionsResponse(
                user.getUsername(),
                toRoleNames(roles),
                toPermissionNames(effectivePermissions)
        );

        return ResponseEntity.ok(response);
    }

    private Set<String> toRoleNames(Set<RoleName> roles) {
        TreeSet<String> names = new TreeSet<>();
        if (roles != null) {
            roles.stream().map(RoleName::name).forEach(names::add);
        }
        return Set.copyOf(names);
    }

    private Set<String> toPermissionNames(Set<Permission> permissions) {
        TreeSet<String> names = new TreeSet<>();
        if (permissions != null) {
            permissions.stream().map(Permission::name).forEach(names::add);
        }
        return Set.copyOf(names);
    }
}
