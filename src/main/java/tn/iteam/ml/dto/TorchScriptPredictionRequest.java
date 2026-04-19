package tn.iteam.ml.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class TorchScriptPredictionRequest {

    @NotEmpty
    private List<Double> features;
}
