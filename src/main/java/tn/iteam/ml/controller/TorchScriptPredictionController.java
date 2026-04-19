package tn.iteam.ml.controller;

import ai.djl.ModelException;
import ai.djl.translate.TranslateException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
public class TorchScriptPredictionController {

    private final TorchScriptPredictionService predictionService;

    @PostMapping("/predict")
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
