package tn.iteam.monitoring.service;

import org.junit.jupiter.api.Test;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonitoringAggregationServiceTest {

    @Test
    void getProblemsDelegatesToCacheServiceAndPreservesDegradedAndFreshness() {
        MonitoringCacheService cacheService = mock(MonitoringCacheService.class);
        MonitoringAggregationService aggregationService = new MonitoringAggregationService(cacheService);

        List<UnifiedMonitoringProblemDTO> problems = List.of(
                UnifiedMonitoringProblemDTO.builder().id("p1").problemId("p1").build()
        );
        when(cacheService.getProblems("ALL")).thenReturn(
                new MonitoringCacheService.FetchResult<>(problems, true, Map.of("OBSERVIUM", "redis_fallback"))
        );

        UnifiedMonitoringResponse<List<UnifiedMonitoringProblemDTO>> response =
                aggregationService.getProblems("ALL");

        assertThat(response.getData()).isEqualTo(problems);
        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getFreshness()).containsEntry("OBSERVIUM", "redis_fallback");
        assertThat(response.getCoverage()).isEmpty();
        verify(cacheService).getProblems("ALL");
    }

    @Test
    void getMetricsExposesCoverageMetadata() {
        MonitoringCacheService cacheService = mock(MonitoringCacheService.class);
        MonitoringAggregationService aggregationService = new MonitoringAggregationService(cacheService);

        List<UnifiedMonitoringMetricDTO> metrics = List.of(
                UnifiedMonitoringMetricDTO.builder().id("m1").itemId("cpu").build()
        );
        when(cacheService.getMetrics(null)).thenReturn(
                new MonitoringCacheService.FetchResult<>(metrics, false, Map.of("ZABBIX", "persisted"))
        );

        UnifiedMonitoringResponse<List<UnifiedMonitoringMetricDTO>> response =
                aggregationService.getMetrics((String) null);

        assertThat(response.getData()).isEqualTo(metrics);
        assertThat(response.isDegraded()).isFalse();
        assertThat(response.getFreshness()).containsEntry("ZABBIX", "persisted");
        assertThat(response.getCoverage())
                .containsEntry("ZABBIX", "native")
                .containsEntry("OBSERVIUM", "synthetic")
                .containsEntry("ZKBIO", "synthetic")
                .containsEntry("CAMERA", "not_applicable");
        verify(cacheService).getMetrics(null);
    }
}
