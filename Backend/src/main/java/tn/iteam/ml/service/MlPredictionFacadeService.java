package tn.iteam.ml.service;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.ml.config.MlRiskProperties;
import tn.iteam.ml.dto.MlHostContext;
import tn.iteam.ml.dto.MlHostFeatureSet;
import tn.iteam.ml.dto.MlPredictionResult;
import tn.iteam.ml.dto.TorchScriptPredictionResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MlPredictionFacadeService {

    private final MlFeatureExtractionService featureExtractionService;
    private final TorchScriptPredictionService predictionService;
    private final MlRiskProperties riskProperties;

    public List<MlPredictionResult> predictHosts(List<MlHostContext> hosts) {
        if (!predictionService.isEnabled()) {
            log.info("ML predictions disabled: {}", predictionService.getDisabledReason());
            return List.of();
        }

        List<MlPredictionResult> results = new ArrayList<>();
        for (MlHostFeatureSet featureSet : featureExtractionService.buildFeatureSets(hosts)) {
            if (!featureSet.predictionReady()) {
                results.add(new MlPredictionResult(
                        featureSet.hostId(),
                        featureSet.hostName(),
                        0,
                        0.0,
                        List.of(),
                        "UNKNOWN",
                        featureSet.featureValues(),
                        "host_without_recent_metrics"
                ));
                continue;
            }

            try {
                TorchScriptPredictionResponse response = predictionService.predict(featureSet.orderedFeatures());
                results.add(new MlPredictionResult(
                        featureSet.hostId(),
                        featureSet.hostName(),
                        response.prediction(),
                        response.probability(),
                        response.probabilities(),
                        resolveStatus(response.probability(), featureSet.featureValues()),
                        featureSet.featureValues(),
                        "ok"
                ));
            } catch (IOException | ModelException | TranslateException | IllegalArgumentException | IllegalStateException exception) {
                log.debug("Unable to compute ML prediction for host {}: {}", featureSet.hostId(), exception.getMessage());
                results.add(new MlPredictionResult(
                        featureSet.hostId(),
                        featureSet.hostName(),
                        0,
                        0.0,
                        List.of(),
                        "UNKNOWN",
                        featureSet.featureValues(),
                        "prediction_failed"
                ));
            }
        }

        return results.stream()
                .sorted((left, right) -> Double.compare(right.probability(), left.probability()))
                .toList();
    }

    private String resolveStatus(double probability, Map<String, Double> features) {
        if (probability >= riskProperties.riskProbabilityThreshold()
                || features.getOrDefault("active_critical_problems", 0.0) > 0.0
                || features.getOrDefault("critical_problem_count_last_1h", 0.0) > 0.0
                || (features.getOrDefault("cpu_usage_percent", 0.0) >= riskProperties.cpuRiskThreshold()
                && features.getOrDefault("ram_usage_percent", 0.0) >= riskProperties.ramRiskThreshold())
                || (features.getOrDefault("latency_ms", 0.0) >= riskProperties.latencyRiskThresholdMs()
                && features.getOrDefault("packet_loss_percent", 0.0) >= riskProperties.packetLossRiskThreshold())) {
            return "RISK";
        }

        if (probability >= riskProperties.watchProbabilityThreshold()
                || features.getOrDefault("cpu_usage_percent", 0.0) >= riskProperties.cpuWatchThreshold()
                || features.getOrDefault("ram_usage_percent", 0.0) >= riskProperties.ramWatchThreshold()
                || features.getOrDefault("latency_ms", 0.0) >= riskProperties.latencyWatchThresholdMs()
                || features.getOrDefault("packet_loss_percent", 0.0) >= riskProperties.packetLossWatchThreshold()
                || features.getOrDefault("problem_count_last_1h", 0.0) > 0.0) {
            return "WATCH";
        }

        return "NORMAL";
    }
}
