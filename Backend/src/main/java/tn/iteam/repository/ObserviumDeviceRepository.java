package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.ObserviumDevice;

import java.util.List;
import java.util.Optional;

public interface ObserviumDeviceRepository extends JpaRepository<ObserviumDevice, Long> {

    List<ObserviumDevice> findByEnabledTrueOrderByIpAddressAsc();

    Optional<ObserviumDevice> findByIpAddress(String ipAddress);
}

