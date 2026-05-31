package tn.iteam.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tn.iteam.auth.dto.KeycloakCredentialRepresentation;
import tn.iteam.auth.dto.KeycloakRoleRepresentation;
import tn.iteam.auth.dto.KeycloakUserSessionRepresentation;
import tn.iteam.auth.dto.KeycloakUserRepresentation;

import java.util.List;

/**
 * Feign client for Keycloak Admin REST API.
 * Handles user creation, lookup, and password management.
 */
@FeignClient(
        name = "keycloak-admin-client",
        url = "${keycloak.base-url}"
)
public interface KeycloakAdminClient {

    /**
     * Create a new user in the realm.
     * Returns 201 Created with Location header containing the new user's ID.
     */
    @PostMapping(
            value = "/admin/realms/{realm}/users",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    feign.Response createUser(
            @PathVariable("realm") String realm,
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody KeycloakUserRepresentation user
    );

    /**
     * Find users by username (exact match).
     */
    @GetMapping("/admin/realms/{realm}/users")
    List<KeycloakUserRepresentation> findUserByUsername(
            @PathVariable("realm") String realm,
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam("username") String username,
            @RequestParam("exact") boolean exact
    );

    @GetMapping("/admin/realms/{realm}/users")
    List<KeycloakUserRepresentation> listUsers(
            @PathVariable("realm") String realm,
            @RequestHeader("Authorization") String bearerToken,
            @RequestParam("first") int first,
            @RequestParam("max") int max,
            @RequestParam("briefRepresentation") boolean briefRepresentation
    );

    @GetMapping("/admin/realms/{realm}/users/{userId}")
    KeycloakUserRepresentation getUserById(
            @PathVariable("realm") String realm,
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken
    );

    @PutMapping(
            value = "/admin/realms/{realm}/users/{userId}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    void updateUser(
            @PathVariable("realm") String realm,
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody KeycloakUserRepresentation user
    );

    /**
     * Set (reset) a user's password.
     */
    @PutMapping(
            value = "/admin/realms/{realm}/users/{userId}/reset-password",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    void resetPassword(
            @PathVariable("realm") String realm,
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody KeycloakCredentialRepresentation credential
    );

    @GetMapping("/admin/realms/{realm}/users/{userId}/role-mappings/realm")
    List<KeycloakRoleRepresentation> getUserRealmRoles(
            @PathVariable("realm") String realm,
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken
    );

    @GetMapping("/admin/realms/{realm}/roles/{roleName}")
    KeycloakRoleRepresentation getRealmRole(
            @PathVariable("realm") String realm,
            @PathVariable("roleName") String roleName,
            @RequestHeader("Authorization") String bearerToken
    );

    @PostMapping(
            value = "/admin/realms/{realm}/users/{userId}/role-mappings/realm",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    void assignRealmRoles(
            @PathVariable("realm") String realm,
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody List<KeycloakRoleRepresentation> roles
    );

    @DeleteMapping(
            value = "/admin/realms/{realm}/users/{userId}/role-mappings/realm",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    void removeRealmRoles(
            @PathVariable("realm") String realm,
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody List<KeycloakRoleRepresentation> roles
    );

    @GetMapping("/admin/realms/{realm}/users/{userId}/sessions")
    List<KeycloakUserSessionRepresentation> getUserSessions(
            @PathVariable("realm") String realm,
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken
    );

    @PostMapping("/admin/realms/{realm}/users/{userId}/logout")
    void logoutUser(
            @PathVariable("realm") String realm,
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken
    );

    @DeleteMapping("/admin/realms/{realm}/users/{userId}")
    void deleteUser(
            @PathVariable("realm") String realm,
            @PathVariable("userId") String userId,
            @RequestHeader("Authorization") String bearerToken
    );
}

