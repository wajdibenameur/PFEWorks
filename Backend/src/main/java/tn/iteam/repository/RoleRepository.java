package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.Role;
import tn.iteam.enums.RoleName;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
