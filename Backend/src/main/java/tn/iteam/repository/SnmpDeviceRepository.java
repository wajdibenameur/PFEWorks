package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.SnmpDevice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SnmpDeviceRepository extends JpaRepository<SnmpDevice, Long> {

    List<SnmpDevice> findByEnabledTrueOrderByIpAddressAsc();

    List<SnmpDevice> findAllByOrderByIpAddressAsc();

    List<SnmpDevice> findByIpAddressInOrderByIpAddressAsc(Collection<String> ipAddresses);

    Optional<SnmpDevice> findByIpAddress(String ipAddress);
}
