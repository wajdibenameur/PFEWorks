package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.ZkBioMetric;

import java.util.Optional;

public interface ZkBioMetricRepository extends JpaRepository<ZkBioMetric, Long> {
    Optional<ZkBioMetric> findByHostIdAndItemIdAndTimestamp(String hostId, String itemId, Long timestamp);
}
