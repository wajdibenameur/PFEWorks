package tn.iteam.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import tn.iteam.enums.Permission;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAuthenticationConverterTest {

    private final KeycloakRolePermissionService rolePermissionService = new KeycloakRolePermissionService();
    private final KeycloakJwtAuthenticationConverter converter =
            new KeycloakJwtAuthenticationConverter(rolePermissionService);

    @Test
    void superadminRoleIsExpandedToAllPermissions() {
        Jwt jwt = jwtWithRoles("SUPERADMIN");

        Authentication authentication = converter.convert(jwt);
        Set<String> authorities = authorityNames(authentication);

        assertThat(authentication).isNotNull();
        assertThat(authorities).contains("ROLE_SUPERADMIN");
        for (Permission permission : Permission.values()) {
            assertThat(authorities).contains(permission.name());
        }
        assertThat(authentication.getName()).isEqualTo("super.user");
    }

    @Test
    void viewerRoleGetsOnlyReadAuthorities() {
        Jwt jwt = jwtWithRoles("VIEWER");

        Authentication authentication = converter.convert(jwt);
        Set<String> authorities = authorityNames(authentication);

        assertThat(authorities).contains("ROLE_VIEWER", Permission.VIEW_TICKETS.name());
        assertThat(authorities).doesNotContain(Permission.CREATE_TICKET.name(), Permission.VALIDATE_TICKET.name());
    }

    @Test
    void resourceRolesAreReadOnlyFromConfiguredClient() {
        KeycloakJwtAuthenticationConverter strictConverter =
                new KeycloakJwtAuthenticationConverter(rolePermissionService, null, null, "auth-service");

        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "subject-123",
                        "preferred_username", "super.user",
                        "realm_access", Map.of("roles", List.of()),
                        "resource_access", Map.of(
                                "auth-service", Map.of("roles", List.of("VIEWER")),
                                "other-client", Map.of("roles", List.of("SUPERADMIN"))
                        )
                )
        );

        Authentication authentication = strictConverter.convert(jwt);
        Set<String> authorities = authorityNames(authentication);

        assertThat(authorities).contains("ROLE_VIEWER");
        assertThat(authorities).doesNotContain("ROLE_SUPERADMIN", Permission.DELETE_USER.name());
    }

    private Jwt jwtWithRoles(String... roles) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of(
                        "sub", "subject-123",
                        "preferred_username", "super.user",
                        "realm_access", Map.of("roles", List.of(roles))
                )
        );
    }

    private Set<String> authorityNames(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
    }
}
