package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.ObserviumMetric;

import java.util.Optional;

public interface ObserviumMetricRepository extends JpaRepository<ObserviumMetric, Long> {
    Optional<ObserviumMetric> findByHostIdAndItemIdAndTimestamp(String hostId, String itemId, Long timestamp);
}
