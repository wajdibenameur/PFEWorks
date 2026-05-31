package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.CameraDevice;

import java.util.List;
import java.util.Optional;

@Repository
public interface CameraDeviceRepository extends JpaRepository<CameraDevice, Long> {

    Optional<CameraDevice> findByIpAddress(String ipAddress);

    List<CameraDevice> findByEnabledTrue();
}

