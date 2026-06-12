package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.iteam.domain.SnmpMetric;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SnmpMetricRepository extends JpaRepository<SnmpMetric, Long> {
    Optional<SnmpMetric> findByHostIdAndItemIdAndTimestamp(String hostId, String itemId, Long timestamp);
    List<SnmpMetric> findByIpInOrderByTimestampDesc(Collection<String> ipAddresses);
}
