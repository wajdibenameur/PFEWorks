package tn.iteam.ml.controller;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.domain.ApiResponse;
import tn.iteam.ml.dto.TorchScriptPredictionRequest;
import tn.iteam.ml.dto.TorchScriptPredictionResponse;
import tn.iteam.ml.service.TorchScriptPredictionService;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_ZABBIX)")
@Tag(name = "Prédiction ML", description = "API de prédiction basée sur le modèle TorchScript")
public class TorchScriptPredictionController {

    private final TorchScriptPredictionService predictionService;

    @PostMapping("/predict")
    @Operation(summary = "Lancer une prédiction", description = "Retourne une prédiction à partir d'un vecteur de caractéristiques numériques.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Prédiction calculée avec succès"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Entrée invalide")
    })
    public ResponseEntity<ApiResponse<TorchScriptPredictionResponse>> predict(
            @Valid @RequestBody TorchScriptPredictionRequest request
    ) throws ModelException, TranslateException, IOException {
        TorchScriptPredictionResponse response = predictionService.predict(request.getFeatures());
        return ResponseEntity.ok(
                ApiResponse.<TorchScriptPredictionResponse>builder()
                        .success(true)
                        .message("TORCHSCRIPT PREDICTION COMPLETED")
                        .source("ML")
                        .data(response)
                        .build()
        );
    }
}
