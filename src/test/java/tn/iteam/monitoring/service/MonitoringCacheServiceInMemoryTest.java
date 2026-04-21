package tn.iteam.monitoring.service;

import org.junit.jupiter.api.Test;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.snapshot.InMemorySnapshotStore;
import tn.iteam.monitoring.snapshot.StoredSnapshot;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringCacheServiceInMemoryTest {

    @Test
    void servesProblemsFromInMemorySnapshotStoreOnly() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        MonitoringCacheService cacheService = new MonitoringCacheService(snapshotStore);

        snapshotStore.save(
                "problems",
                "ZABBIX",
                StoredSnapshot.of(
                        List.of(
                                UnifiedMonitoringProblemDTO.builder()
                                        .id("ZABBIX:p2")
                                        .problemId("p2")
                                        .description("memory-only")
                                        .startedAt(200L)
                                        .build(),
                                UnifiedMonitoringProblemDTO.builder()
                                        .id("ZABBIX:p1")
                                        .problemId("p1")
                                        .description("older")
                                        .startedAt(100L)
                                        .build()
                        ),
                        false,
                        Map.of("ZABBIX", "live")
                )
        );

        MonitoringCacheService.FetchResult<List<UnifiedMonitoringProblemDTO>> result =
                cacheService.getProblems("ZABBIX");

        assertThat(result.isDegraded()).isFalse();
        assertThat(result.getFreshness()).containsEntry("ZABBIX", "live");
        assertThat(result.getData())
                .extracting(UnifiedMonitoringProblemDTO::getProblemId)
                .containsExactly("p2", "p1");
    }

    @Test
    void marksMetricsAsDegradedWhenNoSnapshotExistsInMemory() {
        MonitoringCacheService cacheService = new MonitoringCacheService(new InMemorySnapshotStore());

        MonitoringCacheService.FetchResult<List<UnifiedMonitoringMetricDTO>> result =
                cacheService.getMetrics("OBSERVIUM");

        assertThat(result.isDegraded()).isTrue();
        assertThat(result.getData()).isEmpty();
        assertThat(result.getFreshness()).containsEntry("OBSERVIUM", "snapshot_missing");
    }
}
