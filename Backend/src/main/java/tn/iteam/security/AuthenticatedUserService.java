package tn.iteam.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tn.iteam.domain.Role;
import tn.iteam.domain.User;
import tn.iteam.enums.RoleName;
import tn.iteam.exception.TicketingException;
import tn.iteam.repository.RoleRepository;
import tn.iteam.repository.UserRepository;
import tn.iteam.security.RolePermissionMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class AuthenticatedUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final KeycloakRolePermissionService rolePermissionService;

    public AuthenticatedUserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            KeycloakRolePermissionService rolePermissionService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionService = rolePermissionService;
    }

    public User getCurrentUser() {
        Jwt jwt = currentJwt();
        String username = preferredUsername(jwt);
        String email = normalizeBlank(jwt.getClaimAsString("email"));
        Set<RoleName> roles = rolePermissionService.parseRoles(extractRoleAuthorities());
        return synchronizeUser(username, email, roles);
    }

    public String getCurrentUsername() {
        return preferredUsername(currentJwt());
    }

    public Set<RoleName> getCurrentRoles() {
        return rolePermissionService.parseRoles(extractRoleAuthorities());
    }

    public User synchronizeUser(String username, String email, Set<RoleName> roles) {
        RoleName primaryRole = rolePermissionService.highestPrivilegeRole(roles);

        User user = findExistingUser(username, email)
                .orElseGet(User::new);

        user.setUsername(username);
        user.setEmail(email);
        if (primaryRole != null) {
            user.setRole(ensureLocalRole(primaryRole));
        }
        if (user.getPassword() == null) {
            user.setPassword("EXTERNAL_AUTH");
        }
        if (user.getExtraPermissions() == null) {
            user.setExtraPermissions(new java.util.HashSet<>());
        }
        if (user.getRevokedPermissions() == null) {
            user.setRevokedPermissions(new java.util.HashSet<>());
        }

        return userRepository.save(user);
    }

    private Optional<User> findExistingUser(String username, String email) {
        Optional<User> byUsername = userRepository.findByUsername(username);
        if (byUsername.isPresent()) {
            return byUsername;
        }

        if (email != null) {
            return userRepository.findByEmail(email);
        }
        return Optional.empty();
    }

    private Role ensureLocalRole(RoleName roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(roleName);
                    role.setPermissions(new ArrayList<>(RolePermissionMatrix.permissionsFor(roleName)));
                    return roleRepository.save(role);
                });
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new TicketingException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return jwt;
    }

    private List<String> extractRoleAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()).toUpperCase(Locale.ROOT))
                .toList();
    }

    private String preferredUsername(Jwt jwt) {
        String preferredUsername = normalizeBlank(jwt.getClaimAsString("preferred_username"));
        if (preferredUsername != null) {
            return preferredUsername;
        }

        String email = normalizeBlank(jwt.getClaimAsString("email"));
        if (email != null) {
            return email;
        }

        String subject = normalizeBlank(jwt.getSubject());
        if (subject != null) {
            return subject;
        }

        throw new TicketingException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "JWT principal is missing");
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
