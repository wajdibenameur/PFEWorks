package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.ZabbixMetric;

public interface ZabbixMetricRepository extends JpaRepository<ZabbixMetric, Long> {
}