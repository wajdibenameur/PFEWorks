package tn.iteam.ml.dto;

import java.util.List;
import java.util.Map;

public record MlPredictionResult(
        Long hostId,
        String hostName,
        int prediction,
        double probability,
        List<Double> probabilities,
        String status,
        Map<String, Double> featureValues,
        String reason
) {
}
