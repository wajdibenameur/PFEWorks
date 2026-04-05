package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.MonitoredHost;

public interface MonitoredHostRepository extends JpaRepository<MonitoredHost, String> {
}