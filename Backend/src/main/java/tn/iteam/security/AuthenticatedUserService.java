package tn.iteam.security;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthenticatedUserService {
    private static final ConcurrentHashMap<String, Object> KEYCLOAK_SYNC_LOCKS = new ConcurrentHashMap<>();

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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return getCurrentUser(authentication);
    }

    public User getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            throw new TicketingException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }

        if (!(authentication.getPrincipal() instanceof Jwt)) {
            String username = normalizeBlank(authentication.getName());
            if (username != null) {
                return userRepository.findByUsername(username)
                        .orElseThrow(() -> new TicketingException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"));
            }
            throw new TicketingException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }

        Jwt jwt = extractJwt(authentication);
        Set<RoleName> roles = rolePermissionService.parseRoles(extractRoleAuthorities(authentication));
        return synchronizeUser(jwt, roles);
    }

    public User getCurrentUserByUsername(String username) {
        String normalized = normalizeBlank(username);
        if (normalized == null) {
            throw new TicketingException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return userRepository.findByUsername(normalized)
                .orElseThrow(() -> new TicketingException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"));
    }

    public String getCurrentUsername() {
        return preferredUsername(currentJwt());
    }

    public Set<RoleName> getCurrentRoles() {
        return rolePermissionService.parseRoles(extractRoleAuthorities());
    }

    public User synchronizeUser(String username, String email, Set<RoleName> roles) {
        User user = findExistingUser(null, username, email).orElseGet(User::new);
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);

        Set<Role> syncedRoles = ensureLocalRoles(roles);
        user.setRoles(new HashSet<>(syncedRoles));

        RoleName primaryRole = rolePermissionService.highestPrivilegeRole(roles);
        if (primaryRole != null) {
            user.setRole(ensureLocalRole(primaryRole));
        } else if (!syncedRoles.isEmpty()) {
            user.setRole(syncedRoles.iterator().next());
        }

        if (user.getExtraPermissions() == null) {
            user.setExtraPermissions(new HashSet<>());
        }
        if (user.getRevokedPermissions() == null) {
            user.setRevokedPermissions(new HashSet<>());
        }
        return userRepository.save(user);
    }

    public User synchronizeExternalUser(
            String keycloakId,
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            Set<RoleName> roles
    ) {
        String normalizedKeycloakId = normalizeBlank(keycloakId);
        String normalizedUsername = normalizeBlank(username);
        String normalizedEmail = normalizeBlank(email);

        if (normalizedKeycloakId == null || normalizedUsername == null) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_USER", "Keycloak user id and username are required");
        }

        return withKeycloakSyncLock(normalizedKeycloakId, () -> {
            User user = findExistingUser(normalizedKeycloakId, normalizedUsername, normalizedEmail).orElseGet(User::new);
            user.setKeycloakId(normalizedKeycloakId);
            user.setUsername(normalizedUsername);
            user.setEmail(normalizedEmail);
            user.setFirstName(normalizeBlank(firstName));
            user.setLastName(normalizeBlank(lastName));
            user.setEnabled(enabled);

            Set<Role> syncedRoles = ensureLocalRoles(roles);
            if (!syncedRoles.isEmpty()) {
                user.setRoles(new HashSet<>(syncedRoles));
                RoleName primaryRole = rolePermissionService.highestPrivilegeRole(roles);
                if (primaryRole != null) {
                    user.setRole(ensureLocalRole(primaryRole));
                } else {
                    user.setRole(syncedRoles.iterator().next());
                }
            } else if (user.getRoles() == null) {
                user.setRoles(new HashSet<>());
            }

            if (user.getExtraPermissions() == null) {
                user.setExtraPermissions(new HashSet<>());
            }
            if (user.getRevokedPermissions() == null) {
                user.setRevokedPermissions(new HashSet<>());
            }

            try {
                return userRepository.save(user);
            } catch (DataIntegrityViolationException ex) {
                User persisted = userRepository.findByKeycloakId(normalizedKeycloakId)
                        .orElseThrow(() -> ex);
                persisted.setUsername(normalizedUsername);
                persisted.setEmail(normalizedEmail);
                persisted.setFirstName(normalizeBlank(firstName));
                persisted.setLastName(normalizeBlank(lastName));
                persisted.setEnabled(enabled);
                if (!syncedRoles.isEmpty()) {
                    persisted.setRoles(new HashSet<>(syncedRoles));
                    RoleName primaryRole = rolePermissionService.highestPrivilegeRole(roles);
                    if (primaryRole != null) {
                        persisted.setRole(ensureLocalRole(primaryRole));
                    } else {
                        persisted.setRole(syncedRoles.iterator().next());
                    }
                } else if (persisted.getRoles() == null) {
                    persisted.setRoles(new HashSet<>());
                }
                if (persisted.getExtraPermissions() == null) {
                    persisted.setExtraPermissions(new HashSet<>());
                }
                if (persisted.getRevokedPermissions() == null) {
                    persisted.setRevokedPermissions(new HashSet<>());
                }
                return userRepository.save(persisted);
            }
        });
    }

    public User synchronizeUser(Jwt jwt, Set<RoleName> roles) {
        String keycloakId = normalizeBlank(jwt.getSubject());
        String username = preferredUsername(jwt);
        String email = normalizeBlank(jwt.getClaimAsString("email"));
        String firstName = normalizeBlank(jwt.getClaimAsString("given_name"));
        String lastName = normalizeBlank(jwt.getClaimAsString("family_name"));
        RoleName primaryRole = rolePermissionService.highestPrivilegeRole(roles);

        return withKeycloakSyncLock(keycloakId, () -> {
            User user = findExistingUser(keycloakId, username, email)
                    .orElseGet(User::new);

            user.setKeycloakId(keycloakId);
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(jwt.getClaimAsBoolean("email_verified") == null || Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")));

            Set<Role> syncedRoles = ensureLocalRoles(roles);
            user.setRoles(new HashSet<>(syncedRoles));
            if (primaryRole != null) {
                user.setRole(ensureLocalRole(primaryRole));
            } else if (!syncedRoles.isEmpty()) {
                user.setRole(syncedRoles.iterator().next());
            }

            if (user.getExtraPermissions() == null) {
                user.setExtraPermissions(new HashSet<>());
            }
            if (user.getRevokedPermissions() == null) {
                user.setRevokedPermissions(new HashSet<>());
            }

            try {
                return userRepository.save(user);
            } catch (DataIntegrityViolationException ex) {
                User persisted = userRepository.findByKeycloakId(keycloakId)
                        .orElseThrow(() -> ex);
                persisted.setUsername(username);
                persisted.setEmail(email);
                persisted.setFirstName(firstName);
                persisted.setLastName(lastName);
                persisted.setEnabled(jwt.getClaimAsBoolean("email_verified") == null || Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")));
                persisted.setRoles(new HashSet<>(syncedRoles));
                if (primaryRole != null) {
                    persisted.setRole(ensureLocalRole(primaryRole));
                } else if (!syncedRoles.isEmpty()) {
                    persisted.setRole(syncedRoles.iterator().next());
                }
                if (persisted.getExtraPermissions() == null) {
                    persisted.setExtraPermissions(new HashSet<>());
                }
                if (persisted.getRevokedPermissions() == null) {
                    persisted.setRevokedPermissions(new HashSet<>());
                }
                return userRepository.save(persisted);
            }
        });
    }

    private Optional<User> findExistingUser(String keycloakId, String username, String email) {
        if (keycloakId != null) {
            Optional<User> byKeycloakId = userRepository.findByKeycloakId(keycloakId);
            if (byKeycloakId.isPresent()) {
                return byKeycloakId;
            }
        }

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

    private Set<Role> ensureLocalRoles(Set<RoleName> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return new HashSet<>();
        }

        Set<Role> resolved = new HashSet<>();
        for (RoleName roleName : roleNames) {
            resolved.add(ensureLocalRole(roleName));
        }
        return resolved;
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return extractJwt(authentication);
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new TicketingException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return jwt;
    }

    private List<String> extractRoleAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return extractRoleAuthorities(authentication);
    }

    private List<String> extractRoleAuthorities(Authentication authentication) {
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

    private User withKeycloakSyncLock(String keycloakId, java.util.function.Supplier<User> action) {
        if (keycloakId == null || keycloakId.isBlank()) {
            return action.get();
        }
        Object lock = KEYCLOAK_SYNC_LOCKS.computeIfAbsent(keycloakId, ignored -> new Object());
        synchronized (lock) {
            return action.get();
        }
    }
}
