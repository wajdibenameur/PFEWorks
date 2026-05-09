package tn.iteam.ml.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Entrée de prédiction pour le modèle TorchScript")
public class TorchScriptPredictionRequest {

    @NotEmpty
    @ArraySchema(schema = @Schema(description = "Valeur numérique d'une caractéristique", example = "0.42"),
            arraySchema = @Schema(description = "Vecteur de caractéristiques numériques à fournir au modèle"))
    private List<Double> features;
}
