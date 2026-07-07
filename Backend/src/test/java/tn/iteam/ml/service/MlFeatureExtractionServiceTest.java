package tn.iteam.ml.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.ml.config.MlRiskProperties;
import tn.iteam.ml.dto.MlHostContext;
import tn.iteam.ml.dto.MlHostFeatureSet;
import tn.iteam.repository.ZabbixMetricRepository;
import tn.iteam.repository.ZabbixProblemRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MlFeatureExtractionServiceTest {

    @Mock
    private ZabbixMetricRepository metricRepository;

    @Mock
    private ZabbixProblemRepository problemRepository;

    @Mock
    private TorchScriptPredictionService predictionService;

    private MlFeatureExtractionService service;

    @BeforeEach
    void setUp() {
        MlRiskProperties riskProperties = new MlRiskProperties(
                0.55, 0.80,
                80.0, 90.0,
                80.0, 90.0,
                150.0, 300.0,
                5.0, 15.0,
                4, 4, 5
        );
        service = new MlFeatureExtractionService(metricRepository, problemRepository, predictionService, riskProperties);
    }

    @Test
    void buildsValidatedOrderedFeatureSetFromZabbixMetricsAndProblems() {
        long now = Instant.now().getEpochSecond();
        when(predictionService.getFeatureOrder()).thenReturn(List.of(
                "ram_usage_percent",
                "cpu_usage_percent",
                "availability_status",
                "temperature_celsius",
                "active_critical_problems"
        ));
        when(metricRepository.findRecentMetricsForHosts(anyCollection(), anyLong())).thenReturn(List.of(
                metric("42", "system.cpu.util", 150.0, now - 60),
                metric("42", "vm.memory.util", Double.NaN, now - 50),
                metric("42", "icmpping", 1.0, now - 40),
                metric("42", "sensor.temp", 500.0, now - 30)
        ));
        when(problemRepository.findRecentProblemsForHosts(anyCollection(), anyLong())).thenReturn(List.of(
                problem(42L, "5", true, now - 120)
        ));

        List<MlHostFeatureSet> results = service.buildFeatureSets(List.of(new MlHostContext(42L, "edge-router")));

        assertThat(results).hasSize(1);
        MlHostFeatureSet featureSet = results.get(0);
        assertThat(featureSet.predictionReady()).isTrue();
        assertThat(featureSet.featureValues().get("cpu_usage_percent")).isEqualTo(100.0);
        assertThat(featureSet.featureValues().get("ram_usage_percent")).isEqualTo(0.0);
        assertThat(featureSet.featureValues().get("availability_status")).isEqualTo(1.0);
        assertThat(featureSet.featureValues().get("temperature_celsius")).isEqualTo(150.0);
        assertThat(featureSet.featureValues().get("active_critical_problems")).isEqualTo(1.0);
        assertThat(featureSet.orderedFeatures()).containsExactly(0.0, 100.0, 1.0, 150.0, 1.0);
    }

    @Test
    void marksHostUnknownWhenNoRecentDataExists() {
        when(predictionService.getFeatureOrder()).thenReturn(List.of("cpu_usage_percent", "problem_count_last_1h"));
        when(metricRepository.findRecentMetricsForHosts(anyCollection(), anyLong())).thenReturn(List.of());
        when(problemRepository.findRecentProblemsForHosts(anyCollection(), anyLong())).thenReturn(List.of());

        MlHostFeatureSet featureSet = service.buildFeatureSets(List.of(new MlHostContext(7L, "host-7"))).get(0);

        assertThat(featureSet.predictionReady()).isFalse();
        assertThat(featureSet.status()).isEqualTo("UNKNOWN");
        assertThat(featureSet.orderedFeatures()).containsExactly(0.0, 0.0);
    }

    private ZabbixMetric metric(String hostId, String metricKey, double value, long timestamp) {
        return ZabbixMetric.builder()
                .hostId(hostId)
                .hostName("host-" + hostId)
                .itemId(metricKey + "-item")
                .metricName(metricKey)
                .metricKey(metricKey)
                .valueType(0)
                .value(value)
                .timestamp(timestamp)
                .build();
    }

    private ZabbixProblem problem(Long hostId, String severity, boolean active, long startedAt) {
        return ZabbixProblem.builder()
                .problemId("problem-" + hostId)
                .hostId(hostId)
                .host("host-" + hostId)
                .description("problem")
                .severity(severity)
                .active(active)
                .source("Zabbix")
                .eventId(startedAt)
                .startedAt(startedAt)
                .status(active ? "PROBLEM" : "RESOLVED")
                .build();
    }
}
