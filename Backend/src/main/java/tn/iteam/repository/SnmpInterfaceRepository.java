package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.SnmpInterface;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SnmpInterfaceRepository extends JpaRepository<SnmpInterface, Long> {
    List<SnmpInterface> findByHostIdIn(Collection<String> hostIds);
    Optional<SnmpInterface> findByHostIdAndIfIndex(String hostId, Integer ifIndex);
}
