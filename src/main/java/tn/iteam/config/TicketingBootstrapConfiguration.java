package tn.iteam.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.iteam.domain.Role;
import tn.iteam.domain.User;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;
import tn.iteam.repository.RoleRepository;
import tn.iteam.repository.UserRepository;

import java.util.List;

@Configuration
public class TicketingBootstrapConfiguration {

    @Bean
    CommandLineRunner ticketingBootstrap(RoleRepository roleRepository, UserRepository userRepository) {
        return args -> {
            Role superAdminRole = ensureRole(
                    roleRepository,
                    RoleName.SUPERADMIN,
                    List.of(Permission.values())
            );
            Role adminRole = ensureRole(
                    roleRepository,
                    RoleName.ADMIN,
                    List.of(
                            Permission.VIEW_DASHBOARD,
                            Permission.VIEW_METRICS,
                            Permission.VIEW_HOSTS,
                            Permission.VIEW_TICKETS,
                            Permission.CREATE_TICKET,
                            Permission.EDIT_TICKET,
                            Permission.ASSIGN_TICKET,
                            Permission.VALIDATE_TICKET,
                            Permission.ADD_COMMENT
                    )
            );
            Role supportRole = ensureRole(
                    roleRepository,
                    RoleName.SUPPORT,
                    List.of(
                            Permission.VIEW_DASHBOARD,
                            Permission.VIEW_METRICS,
                            Permission.VIEW_HOSTS,
                            Permission.VIEW_TICKETS,
                            Permission.CREATE_TICKET,
                            Permission.EDIT_TICKET,
                            Permission.ASSIGN_TICKET,
                            Permission.ADD_COMMENT
                    )
            );
            Role viewerRole = ensureRole(
                    roleRepository,
                    RoleName.VIEWER,
                    List.of(Permission.VIEW_DASHBOARD, Permission.VIEW_METRICS, Permission.VIEW_HOSTS, Permission.VIEW_TICKETS)
            );

            ensureUser(userRepository, "superadmin", "superadmin@local", superAdminRole);
            ensureUser(userRepository, "admin", "admin@local", adminRole);
            ensureUser(userRepository, "support", "support@local", supportRole);
            ensureUser(userRepository, "viewer", "viewer@local", viewerRole);
        };
    }

    private Role ensureRole(RoleRepository roleRepository, RoleName roleName, List<Permission> permissions) {
        return roleRepository.findByName(roleName)
                .map(existing -> {
                    existing.setPermissions(permissions);
                    return roleRepository.save(existing);
                })
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(roleName);
                    role.setPermissions(permissions);
                    return roleRepository.save(role);
                });
    }

    private void ensureUser(UserRepository userRepository, String username, String email, Role role) {
        userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPassword("change-me");
            user.setRole(role);
            return userRepository.save(user);
        });
    }
}
