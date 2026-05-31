package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.User;
import tn.iteam.enums.RoleName;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"role"})
    List<User> findAllByOrderByUsernameAsc();

    @EntityGraph(attributePaths = {"role", "roles", "extraPermissions", "revokedPermissions"})
    Optional<User> findWithRolesById(Long id);

    @Query("""
            select distinct u
            from User u
            left join u.role primaryRole
            left join u.roles roleEntry
            where u.enabled = true
              and (primaryRole.name = :roleName or roleEntry.name = :roleName)
            """)
    List<User> findEnabledUsersByRoleName(RoleName roleName);

}
