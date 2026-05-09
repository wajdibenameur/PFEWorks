package tn.iteam.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Résultat retourné par le modèle TorchScript")
public record TorchScriptPredictionResponse(
        @Schema(description = "Classe prédite", example = "1")
        int prediction,
        @Schema(description = "Probabilité associée à la classe prédite", example = "0.93")
        double probability,
        @Schema(description = "Probabilités retournées pour chaque classe")
        List<Double> probabilities,
        @Schema(description = "Ordre attendu des variables d'entrée")
        List<String> featureOrder
) {
}
