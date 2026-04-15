package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.ServiceStatus;

import java.util.Optional;

@Repository
public interface ServiceStatusRepository
        extends JpaRepository<ServiceStatus, Long> {

    Optional<ServiceStatus> findBySourceAndIpAndPort(
            String source, String ip, Integer port);

    Optional<ServiceStatus> findBySourceAndNameAndIp(
            String source, String name, String ip);
}

