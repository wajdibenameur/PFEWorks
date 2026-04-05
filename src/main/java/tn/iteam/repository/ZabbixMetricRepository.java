package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.iteam.domain.ZabbixMetric;

@Repository
public interface ZabbixMetricRepository extends JpaRepository<ZabbixMetric, Long> {
}