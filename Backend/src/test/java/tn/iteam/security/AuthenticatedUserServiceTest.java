package tn.iteam.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.domain.Role;
import tn.iteam.domain.User;
import tn.iteam.enums.RoleName;
import tn.iteam.repository.RoleRepository;
import tn.iteam.repository.UserRepository;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private KeycloakRolePermissionService rolePermissionService;

    @InjectMocks
    private AuthenticatedUserService authenticatedUserService;

    @Test
    @SuppressWarnings("deprecation")
    void synchronizeUserKeepsPasswordNullForExternalAuthentication() {
        Role adminRole = new Role();
        adminRole.setName(RoleName.ADMIN);

        when(rolePermissionService.highestPrivilegeRole(Set.of(RoleName.ADMIN))).thenReturn(RoleName.ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User synchronizedUser = authenticatedUserService.synchronizeUser(
                "alice",
                "alice@example.com",
                Set.of(RoleName.ADMIN)
        );

        assertThat(synchronizedUser.getUsername()).isEqualTo("alice");
        assertThat(synchronizedUser.getEmail()).isEqualTo("alice@example.com");
        assertThat(synchronizedUser.getRole()).isSameAs(adminRole);
        assertThat(synchronizedUser.getPassword()).isNull();
        assertThat(synchronizedUser.getExtraPermissions()).isNotNull();
        assertThat(synchronizedUser.getRevokedPermissions()).isNotNull();
    }
}
