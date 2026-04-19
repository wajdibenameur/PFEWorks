package tn.iteam.ml.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record TorchScriptPredictionResponse(
        int prediction,
        double probability,
        List<Double> probabilities,
        List<String> featureOrder
) {
}
