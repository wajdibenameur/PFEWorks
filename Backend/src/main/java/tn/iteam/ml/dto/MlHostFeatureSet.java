package tn.iteam.ml.dto;

import java.util.List;
import java.util.Map;

public record MlHostFeatureSet(
        Long hostId,
        String hostName,
        Map<String, Double> featureValues,
        List<Double> orderedFeatures,
        boolean predictionReady,
        String status
) {
}
