package tn.iteam.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final KeycloakRolePermissionService rolePermissionService;
    private final EffectiveUserPermissionService effectiveUserPermissionService;
    private final AuthenticatedUserService authenticatedUserService;

    @Autowired
    public KeycloakJwtAuthenticationConverter(
            KeycloakRolePermissionService rolePermissionService,
            EffectiveUserPermissionService effectiveUserPermissionService,
            AuthenticatedUserService authenticatedUserService) {
        this.rolePermissionService = rolePermissionService;
        this.effectiveUserPermissionService = effectiveUserPermissionService;
        this.authenticatedUserService = authenticatedUserService;
    }

    public KeycloakJwtAuthenticationConverter(KeycloakRolePermissionService rolePermissionService) {
        this(rolePermissionService, null, null);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<RoleName> roles = rolePermissionService.parseRoles(extractTokenRoles(jwt));
        String principalName = preferredPrincipal(jwt);
        String email = normalizeBlank(jwt.getClaimAsString("email"));
        Set<Permission> permissions;

        if (effectiveUserPermissionService != null && authenticatedUserService != null) {
            var localUser = authenticatedUserService.synchronizeUser(principalName, email, roles);
            permissions = effectiveUserPermissionService.resolveEffectivePermissions(roles, localUser);
        } else {
            permissions = rolePermissionService.permissionsForRoles(roles);
        }
        Collection<GrantedAuthority> authorities = new LinkedHashSet<>();

        for (RoleName role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        }
        for (Permission permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission.name()));
        }

        return new UsernamePasswordAuthenticationToken(jwt, "n/a", authorities) {
            @Override
            public String getName() {
                return principalName;
            }
        };
    }

    private List<String> extractTokenRoles(Jwt jwt) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        roles.addAll(extractRealmRoles(jwt));
        roles.addAll(extractResourceRoles(jwt));
        return List.copyOf(roles);
    }

    private List<String> extractRealmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmMap)) {
            return List.of();
        }

        return extractRolesClaim(realmMap.get("roles"));
    }

    private List<String> extractResourceRoles(Jwt jwt) {
        Object resourceAccess = jwt.getClaims().get("resource_access");
        if (!(resourceAccess instanceof Map<?, ?> resourceMap)) {
            return List.of();
        }

        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (Object clientAccess : resourceMap.values()) {
            if (!(clientAccess instanceof Map<?, ?> clientMap)) {
                continue;
            }
            roles.addAll(extractRolesClaim(clientMap.get("roles")));
        }
        return List.copyOf(roles);
    }

    private List<String> extractRolesClaim(Object rolesClaim) {
        if (!(rolesClaim instanceof List<?> rawRoles)) {
            return List.of();
        }

        return rawRoles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(Objects::nonNull)
                .toList();
    }

    private String preferredPrincipal(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }

        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            return email;
        }

        return jwt.getSubject();
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
