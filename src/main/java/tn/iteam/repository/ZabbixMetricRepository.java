package tn.iteam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.iteam.domain.ZabbixMetric;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ZabbixMetricRepository extends JpaRepository<ZabbixMetric, Long> {

    Optional<ZabbixMetric> findByHostIdAndItemId(String hostId, String itemId);
    Optional<ZabbixMetric> findByHostIdAndItemIdAndTimestamp(String hostId, String itemId, Long timestamp);
    List<ZabbixMetric> findByHostIdOrderByTimestampDesc(String hostId);
    List<ZabbixMetric> findByHostIdAndTimestampBetweenOrderByTimestampAsc(String hostId, Long start, Long end);
    List<ZabbixMetric> findByTimestampBetweenOrderByTimestampAsc(Long start, Long end);

    @Query("select distinct m.hostId from ZabbixMetric m where m.hostId is not null")
    List<String> findDistinctHostIds();

    @Query("select m from ZabbixMetric m where m.hostId in :hostIds and m.timestamp >= :start order by m.hostId asc, m.timestamp asc")
    List<ZabbixMetric> findRecentMetricsForHosts(Collection<String> hostIds, Long start);
}
