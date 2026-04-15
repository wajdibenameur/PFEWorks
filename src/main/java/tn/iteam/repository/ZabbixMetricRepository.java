package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.ZabbixMetric;

import java.util.Optional;

public interface ZabbixMetricRepository extends JpaRepository<ZabbixMetric, Long> {

    Optional<ZabbixMetric> findByHostIdAndItemId(String hostId, String itemId);
}