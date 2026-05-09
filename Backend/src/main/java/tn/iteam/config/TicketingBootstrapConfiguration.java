package tn.iteam.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.iteam.domain.Role;
import tn.iteam.enums.Permission;
import tn.iteam.enums.RoleName;
import tn.iteam.repository.RoleRepository;
import tn.iteam.security.RolePermissionMatrix;

import java.util.ArrayList;

@Configuration
@ConditionalOnProperty(
        name = "app.bootstrap.ticketing.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class TicketingBootstrapConfiguration {

    @Bean
    CommandLineRunner ticketingBootstrap(RoleRepository roleRepository) {
        return args -> {
            for (RoleName roleName : RoleName.values()) {
                ensureRole(roleRepository, roleName);
            }
        };
    }

    private Role ensureRole(RoleRepository roleRepository, RoleName roleName) {
        var permissions = new ArrayList<>(RolePermissionMatrix.permissionsFor(roleName));
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
}
