package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.MonitoredHost;

import java.util.Optional;

@Repository
public interface MonitoredHostRepository extends JpaRepository<MonitoredHost, Long> {
    Optional<MonitoredHost> findFirstByHostIdAndSourceOrderByIdDesc(String hostId, String source);
}
