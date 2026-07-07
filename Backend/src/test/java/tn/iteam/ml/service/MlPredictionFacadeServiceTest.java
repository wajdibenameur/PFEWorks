package tn.iteam.ml.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.ml.config.MlRiskProperties;
import tn.iteam.ml.dto.MlHostContext;
import tn.iteam.ml.dto.MlHostFeatureSet;
import tn.iteam.ml.dto.MlPredictionResult;
import tn.iteam.ml.dto.TorchScriptPredictionResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MlPredictionFacadeServiceTest {

    @Mock
    private MlFeatureExtractionService featureExtractionService;

    @Mock
    private TorchScriptPredictionService predictionService;

    private MlPredictionFacadeService service;

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
        service = new MlPredictionFacadeService(featureExtractionService, predictionService, riskProperties);
    }

    @Test
    void combinesModelProbabilityAndCriticalProblemsIntoRiskStatus() throws Exception {
        when(predictionService.isEnabled()).thenReturn(true);
        when(featureExtractionService.buildFeatureSets(anyList())).thenReturn(List.of(
                featureSet(1L, "core-sw", Map.of(
                        "active_critical_problems", 1.0,
                        "critical_problem_count_last_1h", 0.0,
                        "cpu_usage_percent", 20.0,
                        "ram_usage_percent", 30.0,
                        "latency_ms", 10.0,
                        "packet_loss_percent", 0.0,
                        "problem_count_last_1h", 0.0
                )),
                featureSet(2L, "db-node", Map.of(
                        "active_critical_problems", 0.0,
                        "critical_problem_count_last_1h", 0.0,
                        "cpu_usage_percent", 81.0,
                        "ram_usage_percent", 40.0,
                        "latency_ms", 20.0,
                        "packet_loss_percent", 0.0,
                        "problem_count_last_1h", 0.0
                ))
        ));
        when(predictionService.predict(List.of(1.0, 2.0))).thenReturn(TorchScriptPredictionResponse.builder()
                .prediction(2)
                .probability(0.40)
                .probabilities(List.of(0.2, 0.4, 0.4))
                .featureOrder(List.of("f1", "f2"))
                .build());
        when(predictionService.predict(List.of(2.0, 3.0))).thenReturn(TorchScriptPredictionResponse.builder()
                .prediction(1)
                .probability(0.60)
                .probabilities(List.of(0.6, 0.2, 0.2))
                .featureOrder(List.of("f1", "f2"))
                .build());

        List<MlPredictionResult> results = service.predictHosts(List.of(
                new MlHostContext(1L, "core-sw"),
                new MlHostContext(2L, "db-node")
        ));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).status()).isEqualTo("WATCH");
        assertThat(results.get(1).status()).isEqualTo("RISK");
    }

    private MlHostFeatureSet featureSet(Long hostId, String hostName, Map<String, Double> featureValues) {
        Map<String, Double> ordered = new LinkedHashMap<>(featureValues);
        return new MlHostFeatureSet(hostId, hostName, ordered, List.of((double) hostId, (double) hostId + 1.0), true, "READY");
    }
}
